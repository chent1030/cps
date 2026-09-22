package com.company.cps.service;

import com.company.cps.config.CpsInitialReviewProperties;
import com.company.cps.domain.CpsInitialReviewItem;
import com.company.cps.domain.CpsInitialReviewResult;
import com.company.cps.domain.CpsInitialReviewTask;
import com.company.cps.domain.CpsInitialReviewTaskStatus;
import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.domain.CpsIssueFlowLog;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.domain.CpsRectificationSubmission;
import com.company.cps.dto.CpsInitialReviewCallbackRequest;
import com.company.cps.mapper.CpsInitialReviewItemMapper;
import com.company.cps.mapper.CpsInitialReviewResultMapper;
import com.company.cps.mapper.CpsInitialReviewTaskMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsIssueFlowLogMapper;
import com.company.cps.mapper.CpsIssueMapper;
import com.company.cps.mapper.CpsRectificationSubmissionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 初审管线（V2 整改域，详细设计 §3.1）：
 * 提交事务内建任务（RUNNING，timeout_at=+600s 可配置）→ 事务提交后投递 C-01（事务外）→
 * Python 回调 C-02 写结果 / 30s 扫描超时置 TIMEOUT_OPEN → 审核专员裁决（REVIEW_CLOSE/REVIEW_REJECT）。
 *
 * Java 是任务状态唯一真相源：Python 只执行与上报，不做任何裁决。
 */
@Service
public class CpsInitialReviewService {

    public static final String SYSTEM_OPERATOR = "SYSTEM";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CpsInitialReviewTaskMapper taskMapper;
    private final CpsInitialReviewResultMapper resultMapper;
    private final CpsInitialReviewItemMapper itemMapper;
    private final CpsRectificationSubmissionMapper submissionMapper;
    private final CpsIssueMapper issueMapper;
    private final CpsIssueAttachmentMapper attachmentMapper;
    private final CpsIssueFlowLogMapper flowLogMapper;
    private final CpsAgentFrameworkClient agentFrameworkClient;
    private final CpsAssignmentService assignmentService;
    private final CpsInitialReviewProperties properties;
    private final CpsWorkflowStateMachineV2 stateMachineV2;

    public CpsInitialReviewService(
            CpsInitialReviewTaskMapper taskMapper,
            CpsInitialReviewResultMapper resultMapper,
            CpsInitialReviewItemMapper itemMapper,
            CpsRectificationSubmissionMapper submissionMapper,
            CpsIssueMapper issueMapper,
            CpsIssueAttachmentMapper attachmentMapper,
            CpsIssueFlowLogMapper flowLogMapper,
            CpsAgentFrameworkClient agentFrameworkClient,
            CpsAssignmentService assignmentService,
            CpsInitialReviewProperties properties,
            CpsWorkflowStateMachineV2 stateMachineV2
    ) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.itemMapper = itemMapper;
        this.submissionMapper = submissionMapper;
        this.issueMapper = issueMapper;
        this.attachmentMapper = attachmentMapper;
        this.flowLogMapper = flowLogMapper;
        this.agentFrameworkClient = agentFrameworkClient;
        this.assignmentService = assignmentService;
        this.properties = properties;
        this.stateMachineV2 = stateMachineV2;
    }

    /**
     * 提交事务内创建初审任务（幂等：同 issue+version 已存在则返回既有任务）。
     * 计时起点=提交成功时刻；timeout_at=submitted_at+timeoutSeconds（默认 600s，PRD §28.4）。
     */
    public CpsInitialReviewTask createTask(Long issueId, Long submissionId, Integer versionNo, LocalDateTime submittedAt) {
        CpsInitialReviewTask existing = taskMapper.findByIssueAndVersion(issueId, versionNo);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        CpsInitialReviewTask task = new CpsInitialReviewTask();
        task.setIssueId(issueId);
        task.setSubmissionId(submissionId);
        task.setVersionNo(versionNo);
        task.setStatus(CpsInitialReviewTaskStatus.RUNNING);
        task.setIdempotencyKey("cps-rectify-" + issueId + "-v" + versionNo);
        task.setSubmittedAt(submittedAt);
        task.setTimeoutAt(submittedAt.plusSeconds(properties.getTimeoutSeconds()));
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException exception) {
            return taskMapper.findByIssueAndVersion(issueId, versionNo);
        }
        return task;
    }

    /**
     * 整改提交事务提交后投递 C-01（事务外投递：DB 状态不受投递失败回滚）。
     * 无活动事务同步（单测环境）时直接投递。
     */
    public void dispatchAfterCommit(Long taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                dispatch(taskId);
            }
        });
    }

    /** C-01 投递：成功回写 review_task_ref；失败置 FAILED（立即可接管）并推进问题单。 */
    void dispatch(Long taskId) {
        CpsInitialReviewTask task = taskMapper.findById(taskId);
        if (task == null || task.getStatus() != CpsInitialReviewTaskStatus.RUNNING) {
            return; // 已终态（含人工接管）不再投递
        }
        try {
            CpsRectificationSubmission submission = submissionMapper.findById(task.getSubmissionId());
            if (submission == null) {
                throw new IllegalStateException("Submission not found: " + task.getSubmissionId());
            }
            CpsIssue issue = issueMapper.findById(task.getIssueId())
                    .orElseThrow(() -> new IllegalStateException("Issue not found: " + task.getIssueId()));
            List<CpsIssueAttachment> beforeImages = attachmentMapper.findByIssueAndStage(issue.getId(), "ISSUE");
            List<CpsIssueAttachment> afterImages = attachmentMapper.findByIssueAndStage(issue.getId(), "PROOF");
            String callbackUrl = properties.getCallbackBaseUrl().replaceAll("/+$", "")
                    + "/api/callbacks/initial-review/result";
            String reviewTaskRef = agentFrameworkClient.triggerInitialReview(
                    submission, issue, beforeImages, afterImages, callbackUrl, task.getId());
            if (reviewTaskRef != null) {
                taskMapper.updateReviewTaskRef(task.getId(), reviewTaskRef);
            }
        } catch (Exception exception) {
            taskMapper.markFailed(task.getId(), "DELIVERY_FAILED", LocalDateTime.now());
            advanceIssueAfterTaskTerminal(task, "delivery failed: " + exception.getMessage());
        }
    }

    /**
     * C-02 回调处理（幂等）：
     * - 重复回调（结果已存在）→ 返回既有信息，不重复写；
     * - 任务已 TAKEN_OVER → 迟到结果：is_late 留痕 + 任务置 LATE_RESULT，不覆盖裁决、不推进流程；
     * - 执行失败回调（error_code 非空）→ 任务 FAILED（立即可接管）；
     * - 正常结果 → 结果+逐项意见（L/P 实际值）落库，任务 COMPLETED，问题单推进。
     */
    @Transactional
    public Map<String, Object> handleCallback(CpsInitialReviewCallbackRequest request) {
        CpsInitialReviewTask task = resolveTask(request);
        if (task == null) {
            throw new IllegalArgumentException("Initial review task not found for callback: "
                    + (notBlank(request.getTaskId()) ? request.getTaskId() : request.getIdempotencyKey()));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task_id", task.getId());
        response.put("issue_id", task.getIssueId());
        response.put("version_no", task.getVersionNo());

        // 幂等：结果已落库 → 不重复写（PRD §3.2 回写幂等键去重）
        CpsInitialReviewResult existing = resultMapper.findByTaskId(task.getId());
        if (existing != null) {
            response.put("received", true);
            response.put("duplicated", true);
            response.put("task_status", task.getStatus().name());
            response.put("is_late", existing.getIsLate());
            return response;
        }

        boolean lateArrival = task.getStatus() == CpsInitialReviewTaskStatus.TAKEN_OVER;
        boolean failureCallback = notBlank(request.getErrorCode());

        if (failureCallback) {
            if (lateArrival) {
                // 迟到失败：仅任务状态留痕，不写结果、不推进
                taskMapper.markLateResult(task.getId(), LocalDateTime.now());
                response.put("received", true);
                response.put("duplicated", false);
                response.put("task_status", CpsInitialReviewTaskStatus.LATE_RESULT.name());
                response.put("is_late", true);
                return response;
            }
            taskMapper.markFailed(task.getId(), request.getErrorCode(), LocalDateTime.now());
            advanceIssueAfterTaskTerminal(task, "execution failed: " + request.getErrorCode());
            response.put("received", true);
            response.put("duplicated", false);
            response.put("task_status", CpsInitialReviewTaskStatus.FAILED.name());
            response.put("is_late", false);
            return response;
        }

        CpsInitialReviewResult result = buildResult(task, request, lateArrival);
        try {
            resultMapper.insert(result);
        } catch (DuplicateKeyException exception) {
            // 并发重复回调：唯一索引兜底
            response.put("received", true);
            response.put("duplicated", true);
            response.put("task_status", task.getStatus().name());
            response.put("is_late", false);
            return response;
        }
        List<CpsInitialReviewItem> items = buildItems(task, result.getId(), request.getItems());
        if (!items.isEmpty()) {
            itemMapper.insertBatch(items);
        }

        if (lateArrival) {
            // PRD §28.4：接管后迟到结果仅留痕供参考，不覆盖人工裁决、不再次推进业务流程
            taskMapper.markLateResult(task.getId(), LocalDateTime.now());
            response.put("task_status", CpsInitialReviewTaskStatus.LATE_RESULT.name());
            response.put("is_late", true);
        } else {
            taskMapper.markCompleted(task.getId(), LocalDateTime.now());
            advanceIssueAfterTaskTerminal(task, "initial review completed");
            response.put("task_status", CpsInitialReviewTaskStatus.COMPLETED.name());
            response.put("is_late", false);
        }
        response.put("received", true);
        response.put("duplicated", false);
        return response;
    }

    /**
     * 30s 扫描（@Scheduled 调用，可配置开关）：
     * 1) RUNNING 且 now≥timeout_at → TIMEOUT_OPEN（≠失败，可接管），问题单推进；
     * 2) AC-25 续路：终态任务 + 问题仍 PENDING_REVIEWER_CONFIG + 审核员现可解析 → PENDING_REVIEW。
     */
    @Transactional
    public int timeoutScan() {
        LocalDateTime now = LocalDateTime.now();
        int moved = 0;
        for (CpsInitialReviewTask task : taskMapper.findExpiredRunning(now)) {
            int updated = taskMapper.markTimeoutOpen(task.getId(), now);
            if (updated == 1) {
                advanceIssueAfterTaskTerminal(task, "initial review timed out");
                moved++;
            }
        }
        for (CpsInitialReviewTask task : taskMapper.findTerminalUnderReviewerConfig()) {
            CpsIssue issue = issueMapper.findById(task.getIssueId()).orElse(null);
            if (issue == null || issue.getStatus() != CpsIssueStatus.PENDING_REVIEWER_CONFIG) {
                continue;
            }
            String reviewer = resolveReviewer(issue, null);
            if (reviewer == null) {
                continue;
            }
            routeToReview(issue, reviewer, CpsIssueAction.REVIEWER_CONFIGURED,
                    "reviewer configured, continue flow (AC-25)");
            moved++;
        }
        return moved;
    }

    /**
     * C-03：主动查询 Python 侧执行状态并按同一规则落地（对账/补偿通道，正常路径靠 C-02 回调）。
     * 返回远端原始状态供界面展示。
     */
    @Transactional
    public Map<String, Object> refreshFromRemote(Long taskId) {
        CpsInitialReviewTask task = taskMapper.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Initial review task not found: " + taskId);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task_id", task.getId());
        response.put("task_status", task.getStatus().name());
        if (task.getReviewTaskRef() == null) {
            response.put("remote", null);
            response.put("message", "task not dispatched yet");
            return response;
        }
        Map<String, Object> remote = agentFrameworkClient.initialReviewStatus(task.getReviewTaskRef());
        response.put("remote", remote);
        Object remoteState = remote == null ? null : remote.get("state");
        if (remoteState != null && "failed".equalsIgnoreCase(String.valueOf(remoteState))
                && task.getStatus() == CpsInitialReviewTaskStatus.RUNNING) {
            taskMapper.markFailed(task.getId(), "REMOTE_REPORTED_FAILED", LocalDateTime.now());
            advanceIssueAfterTaskTerminal(task, "remote reported failed");
            response.put("task_status", CpsInitialReviewTaskStatus.FAILED.name());
        }
        return response;
    }

    /** 审核专员视角的初审详情（任务三态 + 结果 + 逐项意见）。 */
    public Map<String, Object> reviewDetail(Long issueId) {
        CpsInitialReviewTask task = taskMapper.findByIssueAndVersion(issueId, latestVersion(issueId));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("issue_id", issueId);
        detail.put("task", task);
        if (task == null) {
            detail.put("result", null);
            detail.put("items", new ArrayList<>());
            return detail;
        }
        detail.put("result", resultMapper.findByTaskId(task.getId()));
        detail.put("items", itemMapper.findByTaskId(task.getId()));
        return detail;
    }

    /**
     * V2 审核员解析：显式指定 → 问题已存审核员 → 区域规则兜底。
     * W4 将切换为 事项×厂区（cps_item_factory_reviewer），缺失时进入 PENDING_REVIEWER_CONFIG（AC-25）。
     */
    public String resolveReviewer(CpsIssue issue, String explicitReviewerEmpNo) {
        if (notBlank(explicitReviewerEmpNo)) {
            return explicitReviewerEmpNo.trim();
        }
        if (notBlank(issue.getReviewerEmpNo())) {
            return issue.getReviewerEmpNo().trim();
        }
        if (notBlank(issue.getFactory()) && notBlank(issue.getArea())) {
            return assignmentService.findReviewer(issue.getFactory(), issue.getArea());
        }
        return null;
    }

    /** 人工接管（PRD §28.4：必须注明原因；仅 RUNNING/TIMEOUT_OPEN/FAILED 可接管）。 */
    @Transactional
    public Map<String, Object> takeOver(Long taskId, String reviewerEmpNo, String reason) {
        CpsInitialReviewTask task = taskMapper.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Initial review task not found: " + taskId);
        }
        if (!notBlank(reason)) {
            throw new IllegalArgumentException("takeoverReason is required");
        }
        if (!notBlank(reviewerEmpNo)) {
            throw new IllegalArgumentException("reviewerEmpNo is required");
        }
        CpsInitialReviewTaskStatus status = task.getStatus();
        if (status == CpsInitialReviewTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot take over while initial review is still running (threshold not reached, AC-27)");
        }
        if (status != CpsInitialReviewTaskStatus.TIMEOUT_OPEN && status != CpsInitialReviewTaskStatus.FAILED) {
            throw new IllegalStateException("Task in status " + status + " cannot be taken over");
        }
        int updated = taskMapper.markTakenOver(taskId, reviewerEmpNo, reviewerEmpNo, reason, LocalDateTime.now());
        if (updated != 1) {
            throw new IllegalStateException("Takeover conflict, task state changed: " + taskId);
        }
        // 接管后由人工直接审核：确保问题单处于 PENDING_REVIEW 且指向接管人
        issueMapper.findById(task.getIssueId()).ifPresent(issue -> {
            if (issue.getStatus() == CpsIssueStatus.PENDING_AI_REVIEW
                    || issue.getStatus() == CpsIssueStatus.PENDING_REVIEWER_CONFIG) {
                routeToReview(issue, reviewerEmpNo, CpsIssueAction.AI_REVIEW_ADVANCE, "taken over by " + reviewerEmpNo);
            }
        });
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task_id", taskId);
        response.put("task_status", CpsInitialReviewTaskStatus.TAKEN_OVER.name());
        response.put("taken_over_by", reviewerEmpNo);
        return response;
    }

    // ---------- internal ----------

    private Integer latestVersion(Long issueId) {
        CpsRectificationSubmission latest = submissionMapper.findLatestByIssueId(issueId);
        return latest == null ? null : latest.getVersionNo();
    }

    private CpsInitialReviewTask resolveTask(CpsInitialReviewCallbackRequest request) {
        // J0 联调修正：C-01 载荷不含 Java 数字主键，Python 只持有字符串引用。
        // 定位顺序：task_id（数字串→主键；cps-rectify-*→投递幂等键）
        //         → idempotency_key（initial-review-result-{数字|引用}）
        //         → issue_id+version_no 兜底。
        if (notBlank(request.getTaskId())) {
            Long numericId = parseLongOrNull(request.getTaskId());
            if (numericId != null) {
                return taskMapper.findById(numericId);
            }
            return taskMapper.findByIdempotencyKey(request.getTaskId());
        }
        if (notBlank(request.getIdempotencyKey())
                && request.getIdempotencyKey().startsWith("initial-review-result-")) {
            String idPart = request.getIdempotencyKey().substring("initial-review-result-".length());
            Long numericId = parseLongOrNull(idPart);
            if (numericId != null) {
                return taskMapper.findById(numericId);
            }
            return taskMapper.findByIdempotencyKey(idPart);
        }
        if (request.getIssueId() != null && request.getVersionNo() != null) {
            return taskMapper.findByIssueAndVersion(request.getIssueId(), request.getVersionNo());
        }
        return null;
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 任务终态后推进问题单（系统驱动流转）：
     * PENDING_AI_REVIEW → PENDING_REVIEW（审核员可解析）或 PENDING_REVIEWER_CONFIG（无人可审）；
     * 其他状态（已推进/已裁决/已关闭）不动。
     */
    private void advanceIssueAfterTaskTerminal(CpsInitialReviewTask task, String comment) {
        CpsIssue issue = issueMapper.findById(task.getIssueId()).orElse(null);
        if (issue == null) {
            return;
        }
        if (issue.getStatus() != CpsIssueStatus.PENDING_AI_REVIEW) {
            return; // 已被扫描/回调/接管推进过，幂等不重复推进
        }
        String reviewer = firstNonBlank(issue.getReviewerEmpNo(), resolveReviewer(issue, null));
        if (reviewer != null) {
            routeToReview(issue, reviewer, CpsIssueAction.AI_REVIEW_ADVANCE, comment);
        } else {
            stateMachineV2.assertSystemTransition(CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueStatus.PENDING_REVIEWER_CONFIG);
            issueMapper.updateStatus(issue.getId(), CpsIssueStatus.PENDING_REVIEWER_CONFIG, LocalDateTime.now());
            insertFlowLog(issue.getId(), issue.getStatus(), CpsIssueStatus.PENDING_REVIEWER_CONFIG,
                    CpsIssueAction.AI_REVIEW_ADVANCE, issue.getCurrentHandlerEmpNo(), null, comment);
        }
    }

    /** 系统驱动：问题单 → PENDING_REVIEW，当前处理人切到审核员。 */
    private void routeToReview(CpsIssue issue, String reviewerEmpNo, CpsIssueAction action, String comment) {
        stateMachineV2.assertSystemTransition(issue.getStatus(), CpsIssueStatus.PENDING_REVIEW);
        issueMapper.updateReviewRouting(issue.getId(), reviewerEmpNo, reviewerEmpNo,
                CpsIssueStatus.PENDING_REVIEW, LocalDateTime.now());
        insertFlowLog(issue.getId(), issue.getStatus(), CpsIssueStatus.PENDING_REVIEW,
                action, issue.getCurrentHandlerEmpNo(), reviewerEmpNo, comment);
    }

    private CpsInitialReviewResult buildResult(CpsInitialReviewTask task, CpsInitialReviewCallbackRequest request,
                                               boolean lateArrival) {
        CpsInitialReviewResult result = new CpsInitialReviewResult();
        result.setTaskId(task.getId());
        result.setSubmissionId(task.getSubmissionId());
        result.setIssueId(task.getIssueId());
        result.setVersionNo(task.getVersionNo());
        result.setOverall(request.getOverall());
        result.setModelStatus(request.getModelStatus());
        result.setIsLate(lateArrival);
        result.setCallbackIdempotencyKey(request.getIdempotencyKey());
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private List<CpsInitialReviewItem> buildItems(CpsInitialReviewTask task, Long reviewId,
                                                  List<CpsInitialReviewCallbackRequest.Item> requestItems) {
        List<CpsInitialReviewItem> items = new ArrayList<>();
        if (requestItems == null) {
            return items;
        }
        LocalDateTime now = LocalDateTime.now();
        for (CpsInitialReviewCallbackRequest.Item requestItem : requestItems) {
            CpsInitialReviewItem item = new CpsInitialReviewItem();
            item.setReviewId(reviewId);
            item.setTaskId(task.getId());
            item.setCheckType(requestItem.getCheckType());
            item.setFieldName(requestItem.getFieldName());
            item.setVerdict(requestItem.getVerdict());
            item.setTextLength(requestItem.getTextLength());
            item.setPunctuationCount(requestItem.getPunctuationCount());
            item.setRatioOk(requestItem.getRatioOk());
            item.setReason(requestItem.getReason());
            item.setProblemFragment(requestItem.getProblemFragment());
            item.setEvidenceRefs(toJson(requestItem.getEvidenceRefs()));
            item.setConfidence(requestItem.getConfidence());
            item.setCreatedAt(now);
            items.add(item);
        }
        return items;
    }

    private void insertFlowLog(Long issueId, CpsIssueStatus fromStatus, CpsIssueStatus toStatus,
                               CpsIssueAction action, String fromHandler, String toHandler, String comment) {
        CpsIssueFlowLog log = new CpsIssueFlowLog();
        log.setIssueId(issueId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setAction(action);
        log.setOperatorEmpNo(SYSTEM_OPERATOR);
        log.setOperatorEmpName(SYSTEM_OPERATOR);
        log.setFromHandlerEmpNo(fromHandler);
        log.setFromHandlerEmpName(fromHandler);
        log.setToHandlerEmpNo(toHandler);
        log.setToHandlerEmpName(toHandler);
        log.setComment(comment);
        log.setSnapshotJson(snapshotJson(action, comment, fromStatus, toStatus));
        log.setCreatedAt(LocalDateTime.now());
        flowLogMapper.insert(log);
    }

    private static String snapshotJson(CpsIssueAction action, String comment, CpsIssueStatus fromStatus,
                                       CpsIssueStatus toStatus) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("action", action);
        snapshot.put("comment", comment);
        snapshot.put("fromStatus", fromStatus);
        snapshot.put("toStatus", toStatus);
        snapshot.put("operatorEmpNo", SYSTEM_OPERATOR);
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize flow log snapshot", exception);
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize evidence refs", exception);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
