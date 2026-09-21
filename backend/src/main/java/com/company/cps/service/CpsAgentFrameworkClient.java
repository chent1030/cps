package com.company.cps.service;

import com.company.cps.config.CpsAgentFrameworkProperties;
import com.company.cps.dto.CpsIssueCreateRequest;
import com.company.cps.domain.CpsIssueAttachment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Base64;
import java.util.LinkedHashMap;

/** Adapter from the legacy CPS bounded context to the shared Agent runtime. */
@Component
public class CpsAgentFrameworkClient {
    private final CpsAgentFrameworkProperties properties;
    private final RestTemplate client;

    public CpsAgentFrameworkClient(CpsAgentFrameworkProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.client = new RestTemplate(factory);
    }

    public String createAndStart(Long issueId, String employeeNo, CpsIssueCreateRequest request) {
        return createAndStart(issueId, employeeNo, request, java.util.Collections.emptyList());
    }

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
            if (attachment.getContent() == null || attachment.getContent().length == 0) continue;
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("kind", "before");
            evidence.put("note", attachment.getFileName());
            evidence.put("content_base64", Base64.getEncoder().encodeToString(attachment.getContent()));
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
