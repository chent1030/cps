package com.company.cps.service;

import com.company.cps.config.CpsInitialReviewProperties;
import com.company.cps.domain.CpsInitialReviewTask;
import com.company.cps.domain.CpsInitialReviewTaskStatus;
import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.domain.CpsRectificationSubmission;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A3 审核员视图三态 + A4 手动重触发（PRD §28.2/§28.4/§29，AC-27，D-21）：
 * - reviewerView：running/failed/timeout_open 呈现 + can_take_over 判定（RUNNING 未满 10min 不可接管）；
 * - retrigger：仅 FAILED/PENDING_DISPATCH 且未裁决；新幂等键独立计时；PENDING_REVIEW 回退 PENDING_AI_REVIEW。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsInitialReviewA4Test {

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

    // ---------- A3 reviewerView 三态（AC-27：界面必须区分三态） ----------

    private CpsInitialReviewTask task(CpsInitialReviewTaskStatus status, LocalDateTime timeoutAt) {
        CpsInitialReviewTask task = new CpsInitialReviewTask();
        task.setId(501L);
        task.setIssueId(900L);
        task.setSubmissionId(70L);
        task.setVersionNo(1);
        task.setStatus(status);
        task.setSubmittedAt(LocalDateTime.now().minusMinutes(11));
        task.setTimeoutAt(timeoutAt);
        task.setRetryCount(1);
        return task;
    }

    private void mockLatestVersion(int versionNo) {
        CpsRectificationSubmission latest = new CpsRectificationSubmission();
        latest.setIssueId(900L);
        latest.setVersionNo(versionNo);
        when(submissionMapper.findLatestByIssueId(900L)).thenReturn(latest);
    }

    @Test
    void reviewerViewRunningNotExpiredCannotTakeOver() {
        mockLatestVersion(1);
        when(taskMapper.findByIssueAndVersion(900L, 1))
                .thenReturn(task(CpsInitialReviewTaskStatus.RUNNING, LocalDateTime.now().plusMinutes(5)));

        Map<String, Object> view = service.reviewerView(900L);

        assertEquals("running", view.get("state_view"));
        assertEquals(false, view.get("can_take_over"), "RUNNING 未满 10min 不可接管（AC-27）");
        assertNotNull(view.get("seconds_until_takeover"));
        assertTrue((Long) view.get("seconds_until_takeover") > 0);
    }

    @Test
    void reviewerViewRunningExpiredShowsTimeoutOpenAndTakeoverReady() {
        mockLatestVersion(1);
        when(taskMapper.findByIssueAndVersion(900L, 1))
                .thenReturn(task(CpsInitialReviewTaskStatus.RUNNING, LocalDateTime.now().minusMinutes(1)));

        Map<String, Object> view = service.reviewerView(900L);

        assertEquals("timeout_open", view.get("state_view"), "运行满 10min 呈现 timeout_open");
        assertEquals(true, view.get("can_take_over"));
        assertEquals(0L, view.get("seconds_until_takeover"));
    }

    @Test
    void reviewerViewFailedCanTakeOverImmediately() {
        mockLatestVersion(1);
        when(taskMapper.findByIssueAndVersion(900L, 1))
                .thenReturn(task(CpsInitialReviewTaskStatus.FAILED, LocalDateTime.now().plusMinutes(5)));

        Map<String, Object> view = service.reviewerView(900L);

        assertEquals("failed", view.get("state_view"));
        assertEquals(true, view.get("can_take_over"), "失败立即开放接管（AC-27）");
    }

    @Test
    void reviewerViewWithoutTaskShowsNone() {
        when(submissionMapper.findLatestByIssueId(900L)).thenReturn(null);

        Map<String, Object> view = service.reviewerView(900L);

        assertEquals("none", view.get("state_view"));
        assertEquals(false, view.get("can_take_over"));
    }

    // ---------- A4 retrigger ----------

    private void mockRetriggerContext(CpsInitialReviewTaskStatus status, CpsIssueStatus issueStatus) {
        CpsInitialReviewTask task = task(status, LocalDateTime.now());
        task.setIdempotencyKey("cps-rectify-900-v1");
        when(taskMapper.findById(501L)).thenReturn(task);
        CpsIssue issue = new CpsIssue();
        issue.setId(900L);
        issue.setStatus(issueStatus);
        issue.setCurrentHandlerEmpNo(issueStatus == CpsIssueStatus.PENDING_REVIEW ? "E80001" : null);
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(taskMapper.markRetriggered(eq(501L), any(), any(), any())).thenReturn(1);
        when(adjudicationMapper.findByIssueAndVersion(900L, 1)).thenReturn(null);
    }

    @Test
    void retriggerFailedTaskResetsTimingAndRollsBackToAiReview() {
        mockRetriggerContext(CpsInitialReviewTaskStatus.FAILED, CpsIssueStatus.PENDING_REVIEW);

        Map<String, Object> response = service.retrigger(501L, "E1", "deliver failed, retry");

        assertEquals("RUNNING", response.get("task_status"));
        assertEquals("cps-rectify-900-v1-r2", response.get("idempotency_key"), "新幂等键独立投递轮次");
        assertEquals(2, response.get("retry_count"));
        assertNotNull(response.get("timeout_at"), "手动重触发重置计时");
        // PENDING_REVIEW 回退 PENDING_AI_REVIEW（清办理人）+ 流程日志留痕
        verify(issueMapper).updateStatusClearHandler(eq(900L), eq(CpsIssueStatus.PENDING_REVIEW),
                eq(CpsIssueStatus.PENDING_AI_REVIEW), any());
        ArgumentCaptor<com.company.cps.domain.CpsIssueFlowLog> logCaptor =
                ArgumentCaptor.forClass(com.company.cps.domain.CpsIssueFlowLog.class);
        verify(flowLogMapper).insert(logCaptor.capture());
        assertEquals(com.company.cps.domain.CpsIssueAction.AI_REVIEW_RETRIGGER, logCaptor.getValue().getAction());
        assertEquals("E1", logCaptor.getValue().getOperatorEmpNo(), "人工操作者留痕而非 SYSTEM");
    }

    @Test
    void retriggerFailedTaskWhileStillAiReviewKeepsStatus() {
        mockRetriggerContext(CpsInitialReviewTaskStatus.FAILED, CpsIssueStatus.PENDING_AI_REVIEW);

        Map<String, Object> response = service.retrigger(501L, "E1", "retry");

        assertEquals("RUNNING", response.get("task_status"));
        verify(issueMapper, never()).updateStatusClearHandler(anyLong(), any(), any(), any());
    }

    @Test
    void retriggerPendingDispatchTaskOpensFirstDelivery() {
        mockRetriggerContext(CpsInitialReviewTaskStatus.PENDING_DISPATCH, CpsIssueStatus.PENDING_AI_REVIEW);
        // PENDING_DISPATCH 从未投递：retry_count=0，重触发=首次投递轮次
        CpsInitialReviewTask pending = taskMapper.findById(501L);
        pending.setRetryCount(0);
        when(taskMapper.findById(501L)).thenReturn(pending);

        Map<String, Object> response = service.retrigger(501L, "E1", "auto trigger disabled earlier");

        assertEquals("cps-rectify-900-v1-r1", response.get("idempotency_key"));
        assertEquals(1, response.get("retry_count"));
    }

    @Test
    void retriggerRejectedForRunningTask() {
        mockRetriggerContext(CpsInitialReviewTaskStatus.RUNNING, CpsIssueStatus.PENDING_AI_REVIEW);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.retrigger(501L, "E1", "reason"));

        assertTrue(ex.getMessage().contains("Only FAILED or PENDING_DISPATCH"));
    }

    @Test
    void retriggerRejectedAfterAdjudication() {
        mockRetriggerContext(CpsInitialReviewTaskStatus.FAILED, CpsIssueStatus.PENDING_REVIEW);
        when(adjudicationMapper.findByIssueAndVersion(900L, 1)).thenReturn(new com.company.cps.domain.CpsReviewAdjudication());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.retrigger(501L, "E1", "reason"));

        assertTrue(ex.getMessage().contains("already adjudicated"));
        verify(taskMapper, never()).markRetriggered(anyLong(), any(), any(), any());
    }

    @Test
    void retriggerRejectedForClosedIssue() {
        mockRetriggerContext(CpsInitialReviewTaskStatus.FAILED, CpsIssueStatus.CLOSED);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.retrigger(501L, "E1", "reason"));

        assertTrue(ex.getMessage().contains("already closed"));
    }

    @Test
    void retriggerRequiresOperatorAndReason() {
        IllegalArgumentException noOperator = assertThrows(IllegalArgumentException.class,
                () -> service.retrigger(501L, " ", "reason"));
        assertTrue(noOperator.getMessage().contains("operatorEmpNo"));

        IllegalArgumentException noReason = assertThrows(IllegalArgumentException.class,
                () -> service.retrigger(501L, "E1", ""));
        assertTrue(noReason.getMessage().contains("reason"));
    }

    // ---------- A4 自动触发开关（createTask 尊重配置） ----------

    @Test
    void createTaskWithAutoTriggerDisabledStaysPendingDispatch() {
        CpsInitialReviewTriggerConfigLike config = new CpsInitialReviewTriggerConfigLike(false);
        when(configMapper.findGlobal()).thenReturn(config.asDomain());

        CpsInitialReviewTask created = service.createTask(900L, 70L, 1, LocalDateTime.now());

        assertEquals(CpsInitialReviewTaskStatus.PENDING_DISPATCH, created.getStatus(),
                "自动触发关闭：仅建任务不投递，等待 admin 手动重触发");
        verify(agentFrameworkClient, never()).triggerInitialReview(any(), any(), any(), any());
    }

    /** 测试内联配置构造（避免反射设置 Boolean）。 */
    private static final class CpsInitialReviewTriggerConfigLike {
        private final boolean autoTrigger;

        CpsInitialReviewTriggerConfigLike(boolean autoTrigger) {
            this.autoTrigger = autoTrigger;
        }

        com.company.cps.domain.CpsInitialReviewTriggerConfig asDomain() {
            com.company.cps.domain.CpsInitialReviewTriggerConfig config =
                    new com.company.cps.domain.CpsInitialReviewTriggerConfig();
            config.setAutoTriggerEnabled(autoTrigger);
            config.setMaxRetryAttempts(0);
            config.setRetryBackoffMs(0);
            return config;
        }
    }
}
