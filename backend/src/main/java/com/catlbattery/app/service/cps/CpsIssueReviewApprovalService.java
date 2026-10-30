package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsIssue;
import com.catlbattery.domain.entity.cps.CpsIssueAttachment;
import com.catlbattery.domain.entity.cps.CpsIssueStatus;
import com.catlbattery.domain.entity.cps.CpsRectificationSubmission;
import com.catlbattery.infra.mapper.cps.CpsIssueAttachmentMapper;
import com.catlbattery.infra.mapper.cps.CpsIssueMapper;
import com.catlbattery.infra.mapper.cps.CpsRectificationSubmissionMapper;
import com.catlbattery.common.util.CpsStrings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问题整改审核 → web 通用审批中心 的桥接。
 *
 * <p>产品约束（用户 2026-10-28 拍板）：所有审批只在 web 端发生，Android 端不再出现「审核通过/审核退回」。
 * 因此问题进入 PENDING_REVIEW 时，在 {@code cps_approval_instance} 落一条 {@code ISSUE_RECTIFY} 实例，
 * 审批人在 web「审批中心」批准 → 问题 REVIEW_CLOSE → CLOSED；驳回 → REVIEW_REJECT → PENDING_RECTIFY。
 *
 * <p>业务 ID 取 <b>整改提交单 id</b>（{@code cps_rectification_submission.id}）而不是问题 id：
 * 审批实例表有 {@code UNIQUE KEY uk_biz (biz_type, biz_id)}，而同一个问题会被多次驳回重提（version 1/2/3…），
 * 用它自己的提交单做业务 ID，每轮重提天然是一条新审批实例，无需 reopen 已终态实例。
 */
@Service
public class CpsIssueReviewApprovalService {

    /** 审批业务类型：问题整改审核。前端 cps/admin/src/types.ts 里已有同名 label「问题整改」。 */
    public static final String BIZ_TYPE = "ISSUE_RECTIFY";

    private static final Logger log = LoggerFactory.getLogger(CpsIssueReviewApprovalService.class);
    private static final int MAX_TITLE_LENGTH = 80;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CpsApprovalService approvalService;
    private final CpsRectificationSubmissionMapper submissionMapper;
    private final CpsIssueMapper issueMapper;
    private final CpsIssueAttachmentMapper attachmentMapper;
    private final ObjectMapper objectMapper;

    /**
     * 问题裁决服务。用 @Lazy setter 可选注入：CpsIssueService 构造链上依赖 CpsInitialReviewService，
     * 而本服务又被 CpsInitialReviewService 反向引用，直接构造注入会形成循环依赖。
     */
    private CpsIssueService issueService;

    /** AI 初审意见来源（补建历史单时填快照用），同样 @Lazy 可选注入以避免构造期循环。 */
    private CpsInitialReviewService initialReviewService;

    public CpsIssueReviewApprovalService(CpsApprovalService approvalService,
                                        CpsRectificationSubmissionMapper submissionMapper,
                                        CpsIssueMapper issueMapper,
                                        CpsIssueAttachmentMapper attachmentMapper,
                                        ObjectMapper objectMapper) {
        this.approvalService = approvalService;
        this.submissionMapper = submissionMapper;
        this.issueMapper = issueMapper;
        this.attachmentMapper = attachmentMapper;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    public void setIssueService(@Lazy CpsIssueService issueService) {
        this.issueService = issueService;
    }

    @Autowired(required = false)
    public void setInitialReviewService(@Lazy CpsInitialReviewService initialReviewService) {
        this.initialReviewService = initialReviewService;
    }

    /**
     * 保证当前整改版本在审批中心存在待审实例（幂等）。
     *
     * @param issue     已进入 PENDING_REVIEW 的问题
     * @param aiOpinion AI 初审结论摘要，写进快照供审批人参考（可为空）
     * @return 审批实例 id；无提交单或已终态时返回 null（不阻断业务）
     */
    public Long ensurePendingApproval(CpsIssue issue, String aiOpinion) {
        if (issue == null || issue.getId() == null) {
            return null;
        }
        Integer version = issue.getCurrentSubmissionVersion();
        if (version == null || version <= 0) {
            return null;
        }
        CpsRectificationSubmission submission =
                submissionMapper.findByIssueAndVersion(issue.getId(), version);
        if (submission == null) {
            log.warn("Skip issue approval instance: submission missing, issueId={} version={}",
                    issue.getId(), version);
            return null;
        }
        try {
            Long instanceId = approvalService.ensureInstance(
                    BIZ_TYPE,
                    submission.getId(),
                    titleOf(issue),
                    submission.getSubmittedBy(),
                    submission.getSubmittedName(),
                    issue.getFactory(),
                    issue.getArea(),
                    snapshotOf(issue, submission, aiOpinion));
            log.info("Issue review approval ready: issueId={} version={} submissionId={} instanceId={}",
                    issue.getId(), version, submission.getId(), instanceId);
            return instanceId;
        } catch (IllegalStateException ex) {
            // 同一提交单的实例已终态（极端并发下重复 create），业务按幂等处理，不阻断
            log.warn("Skip issue approval instance: issueId={} submissionId={} reason={}",
                    issue.getId(), submission.getId(), ex.getMessage());
            return null;
        }
    }

    /** 审批结果 → 业务回调用的提交单查询。 */
    public CpsRectificationSubmission findSubmission(Long submissionId) {
        return submissionId == null ? null : submissionMapper.findById(submissionId);
    }

    /**
     * 补建历史待审核单的审批实例：审核入口搬到 web 之前已停在 PENDING_REVIEW 的问题单
     * 不会有实例（实例是在进入待审核那一刻建的），由管理员手动触发本方法补齐。
     *
     * @return 本次「已就位」的问题单数（幂等：已有实例的不再新建，仅计入）
     */
    @Transactional
    public int backfillPendingReviews(int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        List<CpsIssue> issues = issueMapper.findByStatus(CpsIssueStatus.PENDING_REVIEW, capped);
        int ensured = 0;
        for (CpsIssue issue : issues) {
            if (ensurePendingApproval(issue, aiOpinionOf(issue)) != null) {
                ensured++;
            }
        }
        log.info("Issue review approval backfill: scanned={} ensured={}", issues.size(), ensured);
        return ensured;
    }

    private String aiOpinionOf(CpsIssue issue) {
        CpsInitialReviewService reviewService = this.initialReviewService;
        if (reviewService == null) {
            return null;
        }
        try {
            return reviewService.aiOpinionSummary(issue.getId(), issue.getCurrentSubmissionVersion());
        } catch (RuntimeException exception) {
            log.warn("Read AI opinion failed: issueId={} reason={}", issue.getId(), exception.getMessage());
            return null;
        }
    }

    /**
     * 审批中心批准/驳回 → 问题单流转（web 端唯一审核入口）。
     *
     * <p>复用 {@link CpsIssueService#adjudicate} 的既有口径（办理人校验、状态机、乐观锁、流程日志、
     * 裁决留痕）：先把「当前办理人」切成 web 审批人，否则会因办理权校验失败而拒单。
     * 陈旧提交单、问题已不在 PENDING_REVIEW、已裁决等情况一律跳过（幂等）。
     *
     * @return true=已驱动问题单流转；false=跳过（无需处理）
     */
    @Transactional
    public boolean handleDecision(Long submissionId, boolean approved, String comment,
                                  String operatorEmpNo, String operatorEmpName) {
        if (issueService == null) {
            log.warn("Skip issue decision: issue service not wired, submissionId={}", submissionId);
            return false;
        }
        CpsRectificationSubmission submission = submissionMapper.findById(submissionId);
        if (submission == null) {
            log.warn("Skip issue decision: submission not found, submissionId={}", submissionId);
            return false;
        }
        CpsIssue issue = issueMapper.findById(submission.getIssueId()).orElse(null);
        if (issue == null) {
            log.warn("Skip issue decision: issue not found, issueId={}", submission.getIssueId());
            return false;
        }
        if (issue.getStatus() != CpsIssueStatus.PENDING_REVIEW) {
            log.info("Skip issue decision: issue {} not in PENDING_REVIEW, status={}",
                    issue.getId(), issue.getStatus());
            return false;
        }
        Integer currentVersion = issue.getCurrentSubmissionVersion();
        if (currentVersion != null && submission.getVersionNo() != null
                && !currentVersion.equals(submission.getVersionNo())) {
            log.info("Skip issue decision: stale submission, issueId={} currentVersion={} submissionVersion={}",
                    issue.getId(), currentVersion, submission.getVersionNo());
            return false;
        }
        String operator = operatorEmpNo == null ? "" : operatorEmpNo.trim();
        if (operator.isEmpty()) {
            throw new IllegalArgumentException("operator employee number is required");
        }
        if (!operator.equals(issue.getCurrentHandlerEmpNo())) {
            // 审批人即审核人：审批中心的操作者接管审核办理权（保留 reviewer 字段与流程一致）
            issueMapper.updateReviewRouting(issue.getId(), operator, operator,
                    CpsIssueStatus.PENDING_REVIEW, LocalDateTime.now());
            log.info("Review handler switched to approver: issueId={} from={} to={}",
                    issue.getId(), issue.getCurrentHandlerEmpNo(), operator);
        }
        String reason = comment == null || CpsStrings.isBlank(comment)
                ? (approved ? "审批中心批准：整改有效，关闭问题" : "审批中心驳回：整改不通过，退回整改人")
                : comment.trim();
        issueService.adjudicate(issue.getId(), approved ? "APPROVE" : "REJECT", reason, operator);
        log.info("Issue adjudicated from approval center: issueId={} approved={} operator={}",
                issue.getId(), approved, operator);
        return true;
    }

    private String titleOf(CpsIssue issue) {
        String text = issue.getDescription();
        if (text == null || CpsStrings.isBlank(text)) {
            return "问题整改审核 #" + issue.getId();
        }
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= MAX_TITLE_LENGTH ? flat : flat.substring(0, MAX_TITLE_LENGTH) + "…";
    }

    private Map<String, Object> snapshotOf(CpsIssue issue, CpsRectificationSubmission submission, String aiOpinion) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("问题ID", issue.getId());
        snapshot.put("巡检类型", valueOrDash(issue.getInspectionTypeCode()));
        snapshot.put("问题描述", valueOrDash(issue.getDescription()));
        snapshot.put("地点", locationOf(issue));
        snapshot.put("上报人", valueOrDash(issue.getCreatorEmpName()));
        snapshot.put("整改版本", submission.getVersionNo());
        snapshot.put("原因分析", valueOrDash(submission.getReason()));
        snapshot.put("短期措施", valueOrDash(submission.getShortTermMeasure()));
        snapshot.put("长期措施", valueOrDash(submission.getLongTermMeasure()));
        snapshot.put("责任员工", personOf(submission.getResponsibleEmpName(), submission.getResponsibleEmpNo()));
        snapshot.put("整改照片", attachmentCountOf(submission.getAttachmentIds()));
        // B1：整改图片列表（前端可直接渲染 img）；无附件或历史单无 attachmentIds 时为空数组
        snapshot.put("整改图片", rectificationImages(submission.getAttachmentIds()));
        snapshot.put("AI 初审意见", valueOrDash(aiOpinion));
        snapshot.put("整改提交人", personOf(submission.getSubmittedName(), submission.getSubmittedBy()));
        snapshot.put("提交时间", submission.getSubmittedAt() == null ? "-" : TIME_FORMAT.format(submission.getSubmittedAt()));
        return snapshot;
    }

    private String locationOf(CpsIssue issue) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{issue.getFactory(), issue.getArea(), issue.getLine(), issue.getProcess()}) {
            if (part != null && !CpsStrings.isBlank(part)) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(part);
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private String personOf(String name, String empNo) {
        String n = name == null ? "" : name.trim();
        String e = empNo == null ? "" : empNo.trim();
        if (n.isEmpty() && e.isEmpty()) {
            return "-";
        }
        if (n.isEmpty()) {
            return e;
        }
        return e.isEmpty() ? n : n + "(" + e + ")";
    }

    private String attachmentCountOf(String attachmentIds) {
        return attachmentIdsOf(attachmentIds).size() + " 张";
    }

    /**
     * B1：整改图片（id + 可直接访问的 url）。
     * url 优先用附件行的 MinIO 公开绝对地址；缺失时回退附件代理路径
     * /api/cps/attachments/{id}/content（安卓真机访问不到 127.0.0.1 时使用）。
     */
    private List<Map<String, Object>> rectificationImages(String attachmentIds) {
        List<Map<String, Object>> images = new ArrayList<>();
        for (Long id : attachmentIdsOf(attachmentIds)) {
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("id", id);
            CpsIssueAttachment attachment = attachmentMapper.findById(id).orElse(null);
            String url = attachment == null ? null : attachment.getFileUrl();
            if (url == null || CpsStrings.isBlank(url)) {
                url = "/api/cps/attachments/" + id + "/content";
            } else {
                url = url.trim();
            }
            image.put("url", url);
            images.add(image);
        }
        return images;
    }

    /**
     * 解析整改附件 id：新数据是 JSON {"before":[...],"after":[...]}（CpsIssueService 写入），
     * 历史数据是逗号分隔 id。两种都兼容，非法 JSON 回退逗号解析，绝不抛异常。
     */
    private List<Long> attachmentIdsOf(String attachmentIds) {
        List<Long> ids = new ArrayList<>();
        if (attachmentIds == null || CpsStrings.isBlank(attachmentIds)) {
            return ids;
        }
        String text = attachmentIds.trim();
        if (text.startsWith("{")) {
            try {
                Map<?, ?> json = objectMapper.readValue(text, Map.class);
                for (Object value : json.values()) {
                    if (value instanceof List) {
                        for (Object item : (List<?>) value) {
                            addAttachmentId(ids, item);
                        }
                    } else {
                        addAttachmentId(ids, value);
                    }
                }
                return ids;
            } catch (Exception e) {
                log.warn("Failed to parse rectify attachmentIds as JSON, fallback to comma split: {}",
                        e.getMessage());
            }
        }
        for (String part : text.split(",")) {
            addAttachmentId(ids, part);
        }
        return ids;
    }

    private static void addAttachmentId(List<Long> ids, Object raw) {
        Long id = null;
        if (raw instanceof Number) {
            id = ((Number) raw).longValue();
        } else if (raw instanceof String) {
            String text = ((String) raw).trim();
            if (text.isEmpty()) return;
            try {
                id = Long.valueOf(text);
            } catch (NumberFormatException ignore) {
                return;
            }
        }
        if (id != null && id > 0 && !ids.contains(id)) {
            ids.add(id);
        }
    }

    private String valueOrDash(String value) {
        return value == null || CpsStrings.isBlank(value) ? "-" : value;
    }
}
