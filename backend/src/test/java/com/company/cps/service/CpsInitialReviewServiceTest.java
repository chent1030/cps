package com.company.cps.service;

import com.company.cps.config.CpsInitialReviewProperties;
import com.company.cps.domain.CpsInitialReviewResult;
import com.company.cps.domain.CpsInitialReviewTask;
import com.company.cps.domain.CpsInitialReviewTaskStatus;
import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.dto.CpsInitialReviewCallbackRequest;
import com.company.cps.mapper.CpsInitialReviewConfigMapper;
import com.company.cps.mapper.CpsInitialReviewEventMapper;
import com.company.cps.mapper.CpsInitialReviewItemMapper;
import com.company.cps.mapper.CpsInitialReviewResultMapper;
import com.company.cps.mapper.CpsInitialReviewTaskMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsIssueFlowLogMapper;
import com.company.cps.mapper.CpsIssueMapper;
import com.company.cps.mapper.CpsRectificationSubmissionMapper;
import com.company.cps.mapper.CpsReviewAdjudicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 初审管线核心语义（PRD §28.4 / 详细设计 §3.1）：
 * 回调幂等、迟到结果仅留痕、超时三态判定、接管约束、投递失败补偿。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsInitialReviewServiceTest {

    @Mock private CpsInitialReviewTaskMapper taskMapper;
    @Mock private CpsInitialReviewResultMapper resultMapper;
    @Mock private CpsInitialReviewItemMapper itemMapper;
    @Mock private CpsRectificationSubmissionMapper submissionMapper;
    @Mock private CpsIssueMapper issueMapper;
    @Mock private CpsIssueAttachmentMapper attachmentMapper;
    @Mock private CpsIssueFlowLogMapper flowLogMapper;
    @Mock private CpsAgentFrameworkClient agentFrameworkClient;
    @Mock private CpsAssignmentService assignmentService;
    @Mock private CpsInitialReviewConfigMapper configMapper;
    @Mock private CpsInitialReviewEventMapper eventMapper;
    @Mock private CpsReviewAdjudicationMapper adjudicationMapper;

    private CpsInitialReviewService service;

    @BeforeEach
    void setUp() {
        CpsInitialReviewProperties properties = new CpsInitialReviewProperties();
        properties.setTimeoutSeconds(600);
        properties.setCallbackBaseUrl("http://127.0.0.1:8080/");
        service = new CpsInitialReviewService(taskMapper, resultMapper, itemMapper, submissionMapper,
                issueMapper, attachmentMapper, flowLogMapper, agentFrameworkClient, assignmentService,
                properties, new CpsWorkflowStateMachineV2(), configMapper, eventMapper, adjudicationMapper);
    }

    // ---------- createTask 幂等 ----------

    @Test
    void createTaskIsIdempotentPerIssueAndVersion() {
        CpsInitialReviewTask existing = task(1L, 101L, 2, CpsInitialReviewTaskStatus.RUNNING);
        when(taskMapper.findByIssueAndVersion(101L, 2)).thenReturn(existing);

        CpsInitialReviewTask result = service.createTask(101L, 55L, 2, LocalDateTime.now());

        assertEquals(Long.valueOf(1L), result.getId());
        verify(taskMapper, never()).insert(any(CpsInitialReviewTask.class));
    }

    @Test
    void createTaskComputesTimeoutFromSubmissionMoment() {
        when(taskMapper.findByIssueAndVersion(101L, 1)).thenReturn(null);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 10, 1, 10, 0, 0);

        service.createTask(101L, 55L, 1, submittedAt);

        ArgumentCaptor<CpsInitialReviewTask> captor = ArgumentCaptor.forClass(CpsInitialReviewTask.class);
        verify(taskMapper).insert(captor.capture());
        CpsInitialReviewTask task = captor.getValue();
        assertEquals(CpsInitialReviewTaskStatus.RUNNING, task.getStatus());
        assertEquals("cps-rectify-101-v1", task.getIdempotencyKey());
        // 计时起点=提交成功时刻，+600s（PRD §28.4）
        assertEquals(submittedAt.plusSeconds(600), task.getTimeoutAt());
    }

    // ---------- 回调幂等 ----------

    @Test
    void duplicateCallbackDoesNotRewriteResult() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.COMPLETED);
        when(taskMapper.findById(9L)).thenReturn(task);
        CpsInitialReviewResult existing = new CpsInitialReviewResult();
        existing.setIsLate(false);
        when(resultMapper.findByTaskId(9L)).thenReturn(existing);

        Map<String, Object> outcome = service.handleCallback(callback(9L, null));

        assertEquals(Boolean.TRUE, outcome.get("duplicated"));
        verify(resultMapper, never()).insert(any(CpsInitialReviewResult.class));
        verify(taskMapper, never()).markCompleted(anyLong(), any(LocalDateTime.class));
        verify(taskMapper, never()).markLateResult(anyLong(), any(LocalDateTime.class));
    }

    // ---------- 正常回调 ----------

    @Test
    void normalCallbackCompletesTaskAndAdvancesIssueToReview() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.RUNNING);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(resultMapper.findByTaskId(9L)).thenReturn(null);
        CpsIssue issue = issue(CpsIssueStatus.PENDING_AI_REVIEW, "R001");
        when(issueMapper.findById(101L)).thenReturn(Optional.of(issue));

        Map<String, Object> outcome = service.handleCallback(callback(9L, null));

        assertEquals(CpsInitialReviewTaskStatus.COMPLETED.name(), outcome.get("task_status"));
        assertEquals(Boolean.FALSE, outcome.get("is_late"));
        ArgumentCaptor<CpsInitialReviewResult> resultCaptor = ArgumentCaptor.forClass(CpsInitialReviewResult.class);
        verify(resultMapper).insert(resultCaptor.capture());
        assertEquals(Boolean.FALSE, resultCaptor.getValue().getIsLate());
        assertEquals("PASS", resultCaptor.getValue().getOverall());
        verify(itemMapper).insertBatch(any());
        verify(taskMapper).markCompleted(eq(9L), any(LocalDateTime.class));
        // 推进：PENDING_AI_REVIEW → PENDING_REVIEW，审核员接手
        verify(issueMapper).updateReviewRouting(eq(101L), eq("R001"), eq("R001"),
                eq(CpsIssueStatus.PENDING_REVIEW), any(LocalDateTime.class));
        verify(issueMapper, never()).updateStatus(anyLong(), any(CpsIssueStatus.class), any(LocalDateTime.class));
    }

    @Test
    void normalCallbackWithoutReviewerRoutesToReviewerConfig() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.RUNNING);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(resultMapper.findByTaskId(9L)).thenReturn(null);
        CpsIssue issue = issue(CpsIssueStatus.PENDING_AI_REVIEW, null);
        issue.setFactory(null);
        issue.setArea(null);
        when(issueMapper.findById(101L)).thenReturn(Optional.of(issue));

        Map<String, Object> outcome = service.handleCallback(callback(9L, null));

        assertEquals(CpsInitialReviewTaskStatus.COMPLETED.name(), outcome.get("task_status"));
        // 无审核员可解析：AC-25 提交保留，进入待配置而非丢失
        verify(issueMapper).updateStatus(eq(101L), eq(CpsIssueStatus.PENDING_REVIEWER_CONFIG), any(LocalDateTime.class));
        verify(issueMapper, never()).updateReviewRouting(anyLong(), anyString(), anyString(),
                any(CpsIssueStatus.class), any(LocalDateTime.class));
    }

    // ---------- 执行失败回调 ----------

    @Test
    void failureCallbackMarksFailedImmediately() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.RUNNING);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(resultMapper.findByTaskId(9L)).thenReturn(null);
        when(issueMapper.findById(101L)).thenReturn(Optional.of(issue(CpsIssueStatus.PENDING_AI_REVIEW, "R001")));

        CpsInitialReviewCallbackRequest request = callback(9L, null);
        request.setErrorCode("MODEL_TIMEOUT");
        Map<String, Object> outcome = service.handleCallback(request);

        // AI 明确执行失败 → FAILED 立即可接管（不等 10 分钟）
        assertEquals(CpsInitialReviewTaskStatus.FAILED.name(), outcome.get("task_status"));
        verify(taskMapper).markFailed(eq(9L), eq("MODEL_TIMEOUT"), any(LocalDateTime.class));
        verify(resultMapper, never()).insert(any(CpsInitialReviewResult.class));
    }

    // ---------- 迟到结果仅留痕 ----------

    @Test
    void lateResultAfterTakeoverIsRecordedWithoutAdvancing() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.TAKEN_OVER);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(resultMapper.findByTaskId(9L)).thenReturn(null);

        Map<String, Object> outcome = service.handleCallback(callback(9L, null));

        // 迟到结果：留痕（LATE_RESULT + is_late）但不覆盖人工裁决、不推进流程
        assertEquals(CpsInitialReviewTaskStatus.LATE_RESULT.name(), outcome.get("task_status"));
        assertEquals(Boolean.TRUE, outcome.get("is_late"));
        verify(taskMapper).markLateResult(eq(9L), any(LocalDateTime.class));
        verify(taskMapper, never()).markCompleted(anyLong(), any(LocalDateTime.class));
        ArgumentCaptor<CpsInitialReviewResult> resultCaptor = ArgumentCaptor.forClass(CpsInitialReviewResult.class);
        verify(resultMapper).insert(resultCaptor.capture());
        assertEquals(Boolean.TRUE, resultCaptor.getValue().getIsLate());
        verify(issueMapper, never()).updateStatus(anyLong(), any(CpsIssueStatus.class), any(LocalDateTime.class));
        verify(issueMapper, never()).updateReviewRouting(anyLong(), anyString(), anyString(),
                any(CpsIssueStatus.class), any(LocalDateTime.class));
    }

    @Test
    void lateFailureCallbackAfterTakeoverOnlyMarksLateResult() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.TAKEN_OVER);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(resultMapper.findByTaskId(9L)).thenReturn(null);

        CpsInitialReviewCallbackRequest request = callback(9L, null);
        request.setErrorCode("MODEL_CRASH");
        Map<String, Object> outcome = service.handleCallback(request);

        assertEquals(CpsInitialReviewTaskStatus.LATE_RESULT.name(), outcome.get("task_status"));
        verify(taskMapper, never()).markFailed(anyLong(), anyString(), any(LocalDateTime.class));
        verify(resultMapper, never()).insert(any(CpsInitialReviewResult.class));
    }

    // ---------- 超时扫描三态 ----------

    @Test
    void timeoutScanMarksExpiredRunningTasksAndAdvancesIssue() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.RUNNING);
        when(taskMapper.findExpiredRunning(any(LocalDateTime.class))).thenReturn(Collections.singletonList(task));
        when(taskMapper.markTimeoutOpen(eq(9L), any(LocalDateTime.class))).thenReturn(1);
        when(issueMapper.findById(101L)).thenReturn(Optional.of(issue(CpsIssueStatus.PENDING_AI_REVIEW, "R001")));
        when(taskMapper.findTerminalUnderReviewerConfig()).thenReturn(Collections.emptyList());

        int moved = service.timeoutScan();

        assertEquals(1, moved);
        // 超时=可接管（TIMEOUT_OPEN），区别于执行失败
        verify(taskMapper).markTimeoutOpen(eq(9L), any(LocalDateTime.class));
        verify(issueMapper).updateReviewRouting(eq(101L), eq("R001"), eq("R001"),
                eq(CpsIssueStatus.PENDING_REVIEW), any(LocalDateTime.class));
    }

    @Test
    void timeoutScanIsIdempotentWhenRowAlreadyMoved() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.TIMEOUT_OPEN);
        when(taskMapper.findExpiredRunning(any(LocalDateTime.class))).thenReturn(Collections.singletonList(task));
        // 并发回调/接管已推进：UPDATE 影响 0 行 → 不重复推进
        when(taskMapper.markTimeoutOpen(eq(9L), any(LocalDateTime.class))).thenReturn(0);
        when(taskMapper.findTerminalUnderReviewerConfig()).thenReturn(Collections.emptyList());

        assertEquals(0, service.timeoutScan());
        verify(issueMapper, never()).findById(anyLong());
    }

    @Test
    void timeoutScanContinuesReviewerConfigFlowOnceReviewerResolvable() {
        // AC-25：审核员补配后继续流转，不要求重新提交
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.COMPLETED);
        when(taskMapper.findExpiredRunning(any(LocalDateTime.class))).thenReturn(Collections.emptyList());
        when(taskMapper.findTerminalUnderReviewerConfig()).thenReturn(Collections.singletonList(task));
        CpsIssue issue = issue(CpsIssueStatus.PENDING_REVIEWER_CONFIG, null);
        issue.setFactory("F1");
        issue.setArea("A1");
        when(issueMapper.findById(101L)).thenReturn(Optional.of(issue));
        when(assignmentService.findReviewer("F1", "A1")).thenReturn("R002");

        int moved = service.timeoutScan();

        assertEquals(1, moved);
        verify(issueMapper).updateReviewRouting(eq(101L), eq("R002"), eq("R002"),
                eq(CpsIssueStatus.PENDING_REVIEW), any(LocalDateTime.class));
    }

    // ---------- 接管约束（AC-27） ----------

    @Test
    void takeOverRejectedWhileRunning() {
        when(taskMapper.findById(9L)).thenReturn(task(9L, 101L, 2, CpsInitialReviewTaskStatus.RUNNING));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.takeOver(9L, "R001", "AI 运行太久"));
        assertTrue(exception.getMessage().contains("AC-27"));
    }

    @Test
    void takeOverAllowedAfterTimeoutOrFailure() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.TIMEOUT_OPEN);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(taskMapper.markTakenOver(eq(9L), eq("R001"), eq("R001"), eq("not timely"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(issueMapper.findById(101L)).thenReturn(Optional.of(issue(CpsIssueStatus.PENDING_AI_REVIEW, null)));

        Map<String, Object> outcome = service.takeOver(9L, "R001", "not timely");

        assertEquals(CpsInitialReviewTaskStatus.TAKEN_OVER.name(), outcome.get("task_status"));
        // 接管即人工直接审核：单据转 PENDING_REVIEW 指向接管人
        verify(issueMapper).updateReviewRouting(eq(101L), eq("R001"), eq("R001"),
                eq(CpsIssueStatus.PENDING_REVIEW), any(LocalDateTime.class));
    }

    @Test
    void takeOverRequiresReason() {
        when(taskMapper.findById(9L)).thenReturn(task(9L, 101L, 2, CpsInitialReviewTaskStatus.FAILED));
        assertThrows(IllegalArgumentException.class, () -> service.takeOver(9L, "R001", "  "));
    }

    // ---------- C-01 投递与失败补偿 ----------

    @Test
    void dispatchSuccessStoresReviewTaskRef() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.RUNNING);
        task.setSubmissionId(55L);
        when(taskMapper.findById(9L)).thenReturn(task);
        com.company.cps.domain.CpsRectificationSubmission submission =
                new com.company.cps.domain.CpsRectificationSubmission();
        submission.setId(55L);
        submission.setIssueId(101L);
        submission.setVersionNo(2);
        when(submissionMapper.findById(55L)).thenReturn(submission);
        when(issueMapper.findById(101L)).thenReturn(Optional.of(issue(CpsIssueStatus.PENDING_AI_REVIEW, "R001")));
        when(attachmentMapper.findByIssueAndStage(101L, "ISSUE")).thenReturn(Collections.emptyList());
        when(attachmentMapper.findByIssueAndStage(101L, "PROOF")).thenReturn(Collections.emptyList());
        when(agentFrameworkClient.triggerInitialReview(any(), any(), any(), any(), anyString(), eq(9L)))
                .thenReturn("rr-90");

        service.dispatchAfterCommit(9L);

        verify(taskMapper).updateReviewTaskRef(9L, "rr-90");
        verify(taskMapper, never()).markFailed(anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void dispatchFailureMarksTaskFailedAndAdvancesIssue() {
        CpsInitialReviewTask task = task(9L, 101L, 2, CpsInitialReviewTaskStatus.RUNNING);
        task.setSubmissionId(55L);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(submissionMapper.findById(55L)).thenReturn(null); // 触发投递异常

        service.dispatchAfterCommit(9L);

        verify(taskMapper).markFailed(eq(9L), eq("DELIVERY_FAILED"), any(LocalDateTime.class));
        verify(taskMapper, never()).updateReviewTaskRef(anyLong(), anyString());
    }

    @Test
    void dispatchSkipsTerminalTasks() {
        when(taskMapper.findById(9L)).thenReturn(task(9L, 101L, 2, CpsInitialReviewTaskStatus.TAKEN_OVER));
        service.dispatchAfterCommit(9L);
        verify(agentFrameworkClient, never()).triggerInitialReview(any(), any(), any(), any(), anyString(), anyLong());
    }

    // ---------- fixtures ----------

    private CpsInitialReviewTask task(Long id, Long issueId, int versionNo, CpsInitialReviewTaskStatus status) {
        CpsInitialReviewTask task = new CpsInitialReviewTask();
        task.setId(id);
        task.setIssueId(issueId);
        task.setSubmissionId(55L);
        task.setVersionNo(versionNo);
        task.setStatus(status);
        task.setIdempotencyKey("cps-rectify-" + issueId + "-v" + versionNo);
        task.setSubmittedAt(LocalDateTime.now().minusMinutes(15));
        task.setTimeoutAt(LocalDateTime.now().minusMinutes(5));
        return task;
    }

    private CpsIssue issue(CpsIssueStatus status, String reviewerEmpNo) {
        CpsIssue issue = new CpsIssue();
        issue.setId(101L);
        issue.setStatus(status);
        issue.setFlowVersion("v2");
        issue.setFactory("F1");
        issue.setArea("A1");
        issue.setReviewerEmpNo(reviewerEmpNo);
        return issue;
    }

    private CpsInitialReviewCallbackRequest callback(Long taskId, String idempotencyKey) {
        CpsInitialReviewCallbackRequest request = new CpsInitialReviewCallbackRequest();
        request.setTaskId(taskId == null ? null : String.valueOf(taskId));
        request.setIdempotencyKey(idempotencyKey);
        request.setSubmissionId(55L);
        request.setIssueId(101L);
        request.setVersionNo(2);
        request.setOverall("PASS");
        request.setModelStatus("ok");
        CpsInitialReviewCallbackRequest.Item item = new CpsInitialReviewCallbackRequest.Item();
        item.setCheckType("TEXT_LENGTH");
        item.setFieldName("reason");
        item.setVerdict("PASS");
        item.setTextLength(20);
        item.setPunctuationCount(1);
        item.setRatioOk(Boolean.TRUE);
        request.setItems(Collections.singletonList(item));
        return request;
    }
}
