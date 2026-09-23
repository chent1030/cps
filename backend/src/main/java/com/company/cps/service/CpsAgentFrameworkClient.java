package com.company.cps.service;

import com.company.cps.config.CpsAgentFrameworkProperties;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsRectificationSubmission;
import com.company.cps.dto.CpsIssueCreateRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adapter from the CPS bounded context to the shared Agent runtime. */
@Component
public class CpsAgentFrameworkClient {
    private final CpsAgentFrameworkProperties properties;
    private final CpsAttachmentContentResolver contentResolver;
    private final RestTemplate client;

    @org.springframework.beans.factory.annotation.Autowired
    public CpsAgentFrameworkClient(CpsAgentFrameworkProperties properties, CpsAttachmentContentResolver contentResolver) {
        this.properties = properties;
        this.contentResolver = contentResolver;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.client = new RestTemplate(factory);
    }

    /** 测试专用：注入预绑定 MockRestServiceServer 的 RestTemplate（包级可见）。 */
    CpsAgentFrameworkClient(CpsAgentFrameworkProperties properties, CpsAttachmentContentResolver contentResolver, RestTemplate restTemplate) {
        this.properties = properties;
        this.contentResolver = contentResolver;
        this.client = restTemplate;
    }

    public String createAndStart(Long issueId, String employeeNo, CpsIssueCreateRequest request) {
        return createAndStart(issueId, employeeNo, request, java.util.Collections.emptyList());
    }

    /**
     * R-N1/P0 修复：附件内容统一经 {@link CpsAttachmentContentResolver} 解析——
     * 新附件（content 为 NULL，仅 RustFS object_key）按流读取转发，
     * 旧附件（content 非空）保留 base64 兼容路径；
     * RustFS 读取失败快速失败，不再静默跳过（否则退化为无图巡检）。
     */
    public String createAndStart(Long issueId, String employeeNo, CpsIssueCreateRequest request, List<CpsIssueAttachment> attachments) {
        if (!properties.isEnabled()) return null;
        Map<String, Object> payload = new HashMap<>();
        payload.put("goal", request.getDescription());
        Map<String, Object> line = new HashMap<>();
        line.put("line_id", request.getLine());
        line.put("area", request.getArea());
        line.put("supervisor_id", request.getFeedbackEmpNo() == null ? employeeNo : request.getFeedbackEmpNo());
        line.put("modifications", "factory=" + request.getFactory() + "; process=" + request.getProcess());
        payload.put("line_info", line);
        payload.put("required_outputs", Arrays.asList("issues", "report", "work_plan"));
        payload.put("idempotency_key", "legacy-cps-issue-" + issueId);

        HttpHeaders headers = headers();
        Map response = client.postForObject(url("/cps/inspections"), new HttpEntity<>(payload, headers), Map.class);
        if (response == null || response.get("id") == null) throw new IllegalStateException("Agent framework returned no inspection id");
        String inspectionId = String.valueOf(response.get("id"));
        for (CpsIssueAttachment attachment : attachments) {
            byte[] content = contentResolver.resolve(attachment);
            if (content == null || content.length == 0) continue;
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("kind", "before");
            evidence.put("note", attachment.getFileName());
            evidence.put("content_base64", Base64.getEncoder().encodeToString(content));
            evidence.put("expected_version", response.get("version"));
            evidence.put("idempotency_key", "legacy-cps-evidence-" + issueId + "-" + attachment.getId());
            Map evidenceResponse = client.postForObject(url("/cps/inspections/" + inspectionId + "/evidence"), new HttpEntity<>(evidence, headers), Map.class);
            if (evidenceResponse != null && evidenceResponse.get("version") != null) {
                response.put("version", evidenceResponse.get("version"));
            }
        }
        Map<String, Object> command = new HashMap<>();
        command.put("expected_version", response.get("version"));
        command.put("idempotency_key", "legacy-cps-start-" + issueId);
        client.postForObject(url("/cps/inspections/" + inspectionId + "/start"), new HttpEntity<>(command, headers), Map.class);
        return inspectionId;
    }

    /**
     * C-01：触发整改初审（事务提交后投递，POST {base}/agent/rectifications）。
     * 波次7 J线（C7 冻结 schema，additionalProperties=false）：必填 issue_id/submission_id/version_no；
     * 可选 reason/short_term_measure/long_term_measure/before_attachments/after_attachments/issue_snapshot。
     * 附件走 AttachmentRef.object_key（cps_issue_attachment.file_url 即 RustFS object key，Python 自取流，
     * 避免大图 base64 内联——联调清单④同口径）；幂等 task_ref 由 Python 派生（cps-rectify-{issue_id}-v{version_no}）；
     * 回调地址由 Python 配置（JavaCallbackConfig base_url + 固定 path），不经请求体传递。
     *
     * @return review_task_ref（Python 侧初审任务引用；client 禁用时返回 null 不投递）
     */
    public String triggerInitialReview(
            CpsRectificationSubmission submission,
            CpsIssue issue,
            List<CpsIssueAttachment> beforeImages,
            List<CpsIssueAttachment> afterImages
    ) {
        if (!properties.isEnabled()) return null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issue_id", String.valueOf(issue.getId()));
        payload.put("submission_id", String.valueOf(submission.getId()));
        payload.put("version_no", submission.getVersionNo());
        payload.put("reason", submission.getReason());
        payload.put("short_term_measure", submission.getShortTermMeasure());
        payload.put("long_term_measure", submission.getLongTermMeasure());

        payload.put("before_attachments", attachmentRefs(beforeImages));
        payload.put("after_attachments", attachmentRefs(afterImages));

        Map<String, Object> issueSnapshot = new LinkedHashMap<>();
        issueSnapshot.put("issue_id", String.valueOf(issue.getId()));
        issueSnapshot.put("factory", issue.getFactory());
        issueSnapshot.put("area", issue.getArea());
        issueSnapshot.put("line", issue.getLine());
        issueSnapshot.put("process", issue.getProcess());
        issueSnapshot.put("description", issue.getDescription());
        payload.put("issue_snapshot", issueSnapshot);

        Map response = client.postForObject(url("/agent/rectifications"), new HttpEntity<>(payload, headers()), Map.class);
        if (response == null) {
            throw new IllegalStateException("Agent framework returned no rectification review response");
        }
        Object ref = response.get("review_task_ref") != null ? response.get("review_task_ref") : response.get("task_id");
        if (ref == null) {
            throw new IllegalStateException("Agent framework returned no review_task_ref for submission "
                    + submission.getId());
        }
        return String.valueOf(ref);
    }

    /**
     * C-01 附件引用（AttachmentRef，additionalProperties=false）：
     * 优先 object_key（file_url 即 RustFS object key，Python 侧自取流）；
     * 无 object_key 的历史附件回退 content_base64（经 contentResolver 读流）。
     */
    private List<Map<String, Object>> attachmentRefs(List<CpsIssueAttachment> images) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        if (images == null) return payloads;
        for (CpsIssueAttachment image : images) {
            Map<String, Object> item = new LinkedHashMap<>();
            // AttachmentRef.attachment_id 为 str 类型（pydantic 严格校验不收数字）——统一字符串化
            item.put("attachment_id", image.getId() == null ? null : String.valueOf(image.getId()));
            item.put("file_name", image.getFileName());
            if (image.getFileUrl() != null && !image.getFileUrl().trim().isEmpty()) {
                item.put("object_key", image.getFileUrl());
            } else {
                byte[] content = contentResolver.resolve(image);
                if (content == null || content.length == 0) continue;
                item.put("content_base64", Base64.getEncoder().encodeToString(content));
            }
            payloads.add(item);
        }
        return payloads;
    }

    /** C-03：查询 Python 侧初审执行状态（GET {base}/api/agent/rectifications/{ref}）；三态由 Java 判定。 */
    public Map<String, Object> initialReviewStatus(String reviewTaskRef) {
        if (!properties.isEnabled()) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("enabled", false);
            return status;
        }
        Map<String, Object> status = client.getForObject(url("/agent/rectifications/" + reviewTaskRef), Map.class);
        return status == null ? new LinkedHashMap<>() : status;
    }

    /**
     * C-07：调 Python 计划 Agent 生成巡检计划草稿。
     * POST {base}/api/agent/inspection-plans/draft；幂等键 plan-draft-{sourceRunId}。
     * 返回 Map 内含 draft_content_json / title / tasks 建议等，由 Java 落 cps_inspection_plan。
     * Python 侧实现可后置，本期 Python 缺位时 client 禁用或返回 5xx 由 service 兜底降级——保存请求而非拒绝。
     */
    public Map<String, Object> requestInspectionPlanDraft(
            String sourceRunId,
            String planType,
            String title,
            String factory,
            String area,
            String riskBasis) {
        if (!properties.isEnabled()) return null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idempotency_key", "plan-draft-" + sourceRunId);
        payload.put("source_run_id", sourceRunId);
        payload.put("plan_type", planType);
        payload.put("title", title);
        if (factory != null) payload.put("factory", factory);
        if (area != null) payload.put("area", area);
        if (riskBasis != null) payload.put("risk_basis", riskBasis);
        Map response = client.postForObject(url("/agent/inspection-plans/draft"),
                new HttpEntity<>(payload, headers()), Map.class);
        return response == null ? new LinkedHashMap<>() : response;
    }

    /**
     * C-05/C-08：admin 端周报运行记录查询（PRD §21.3；AC-04/05）。
     * 波次7 J线对齐 Python 实际契约：GET {base}/agent/weekly-reports
     * （Query：report_type/status/push_status/period/limit/offset；响应 {"items":[...],"total":n}）。
     * period 过滤（周窗 window_start/window_end）由 service 映射后在 Java 侧执行。
     * 返回原始行 Map 列表，由 service 归一化。
     */
    public List<Map<String, Object>> listWeeklyReportRuns(
            String inspectionType,
            String status,
            String periodStart,
            String periodEnd) {
        if (!properties.isEnabled()) return new java.util.ArrayList<>();
        StringBuilder query = new StringBuilder();
        if (inspectionType != null) query.append("&report_type=").append(inspectionType);
        if (status != null) query.append("&status=").append(status);
        query.append("&limit=200");
        String url = url("/agent/weekly-reports")
                + (query.length() == 0 ? "" : "?" + query.substring(1));
        Map<String, Object> response = client.getForObject(url, Map.class);
        if (response == null) return new java.util.ArrayList<>();
        Object items = response.get("items");
        if (!(items instanceof List)) {
            return new java.util.ArrayList<>();
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) items;
        return rows;
    }

    /**
     * C-05/C-08：受控下载周报文件流。Python 契约：GET {base}/agent/weekly-reports/{run_id}/download
     * （校验 internal_trust；run 需有归档产物）。Java 侧不做二次权限过滤（admin 端已在 controller 校验）。
     * 返回字节数组 + contentType；Java 写入 HttpServletResponse 流。
     */
    public WeeklyReportFile downloadWeeklyReportFile(String runId) {
        if (!properties.isEnabled()) return null;
        org.springframework.http.ResponseEntity<byte[]> response = client.exchange(
                url("/agent/weekly-reports/" + runId + "/download"),
                org.springframework.http.HttpMethod.GET,
                null,
                byte[].class);
        if (response == null || response.getBody() == null) return null;
        org.springframework.http.HttpHeaders headers = response.getHeaders();
        String contentType = headers.getContentType() == null
                ? "application/octet-stream" : headers.getContentType().toString();
        String fileName = headers.getFirst("Content-Disposition");
        return new WeeklyReportFile(response.getBody(), contentType, fileName);
    }

    /** 受控下载周报文件包装（C-05/C-08）。 */
    public static class WeeklyReportFile {
        private final byte[] content;
        private final String contentType;
        private final String contentDisposition;

        public WeeklyReportFile(byte[] content, String contentType, String contentDisposition) {
            this.content = content;
            this.contentType = contentType;
            this.contentDisposition = contentDisposition;
        }
        public byte[] getContent() { return content; }
        public String getContentType() { return contentType; }
        public String getContentDisposition() { return contentDisposition; }
    }

    /** Read-only admin projections are exposed through CPS so the browser never talks to Agent runtime directly. */
    public Map<String, Object> runtimeStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("baseUrl", properties.getBaseUrl());
        status.put("tenantId", properties.getTenantId());
        if (!properties.isEnabled()) {
            status.put("state", "DISABLED");
            status.put("message", "Agent 编排未启用。请设置 CPS_AGENT_FRAMEWORK_ENABLED=true 后重启 CPS 服务。");
            return status;
        }
        try {
            Map metrics = client.getForObject(url("/cps/metrics"), Map.class);
            status.put("state", "ONLINE");
            status.put("message", "basic-project Agent 运行时连接正常");
            status.put("metrics", metrics == null ? new LinkedHashMap<>() : metrics);
        } catch (Exception exception) {
            status.put("state", "UNAVAILABLE");
            status.put("message", "无法连接 basic-project Agent 运行时：" + exception.getMessage());
        }
        return status;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return properties.getBaseUrl().replaceAll("/$", "") + path;
    }
}
