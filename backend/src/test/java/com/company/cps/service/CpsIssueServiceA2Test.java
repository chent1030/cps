package com.company.cps.service;

import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueFlowLog;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.dto.CpsIssueActionRequest;
import com.company.cps.dto.CpsIssueActionResponse;
import com.company.cps.mapper.CpsIssueAiSuggestionMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsIssueFlowLogMapper;
import com.company.cps.mapper.CpsIssueMapper;
import com.company.cps.mapper.CpsRectificationSubmissionMapper;
import com.company.cps.mapper.CpsRectificationTransferMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A2 编辑/转办锁定 + 审核员改配 + 乐观锁（PRD §28.3/§28.4/§30.2，AC-24/25/26）：
 * - 版本锁定态（AI 初审中/待配置/待人工审核）拒绝编辑类动作；
 * - 仅整改办理中（PENDING_RECTIFY）可编辑/转办；PENDING_FEEDBACK 转办是反馈改派不受锁约束；
 * - 改配：未完成审核单转新审核员、原审核员失权、不重置初审计时、待配置单续路；
 * - 乐观锁：CAS 更新 0 行受影响 → 冲突异常。
 */
@ExtendWith(MockitoExtension.class)
class CpsIssueServiceA2Test {

    @Mock private CpsIssueMapper issueMapper;
    @Mock private CpsIssueAttachmentMapper attachmentMapper;
    @Mock private CpsIssueAiSuggestionMapper aiSuggestionMapper;
    @Mock private CpsIssueFlowLogMapper flowLogMapper;
    @Mock private CpsAssignmentService assignmentService;
    @Mock private CpsAgentFrameworkClient agentFrameworkClient;
    @Mock private CpsRectificationSubmissionMapper submissionMapper;
    @Mock private CpsRectificationTransferMapper transferMapper;
    @Mock private CpsInitialReviewService initialReviewService;

    private CpsIssueService service;
    private final CpsWorkflowStateMachineV2 stateMachineV2 = new CpsWorkflowStateMachineV2();

    @BeforeEach
    void setUp() {
        service = new CpsIssueService(
                issueMapper, attachmentMapper, aiSuggestionMapper, flowLogMapper, assignmentService,
                new CpsWorkflowStateMachine(), agentFrameworkClient, stateMachineV2,
                submissionMapper, transferMapper, initialReviewService
        );
    }

    // ---------- AC-26 编辑/转办锁定 ----------

    @Test
    void saveDraftRejectedWhileAiReviewLocksVersion() {
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_AI_REVIEW, null);
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        // 初审中 current_handler 为空：占位 currentEmpNo=null 通过办理人校验以触达锁定守卫
        assertLocked(issue, action(CpsIssueAction.SAVE_DRAFT), null);
    }

    @Test
    void submitRectificationRejectedWhileReviewerConfigPending() {
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_REVIEWER_CONFIG, null);
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        CpsIssueActionRequest request = action(CpsIssueAction.SUBMIT_RECTIFICATION);
        request.setReasonAnalysis("r");
        request.setShortTermMeasure("s");
        request.setLongTermMeasure("l");
        request.setResponsibleEmpNo("E30001");
        request.setProofAttachmentIds(java.util.Collections.singletonList(1L));
        assertLocked(issue, request, null);
    }

    @Test
    void transferRejectedWhilePendingReviewLocksVersion() {
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_REVIEW, "E80001");
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        CpsIssueActionRequest request = action(CpsIssueAction.TRANSFER);
        request.setTargetEmpNo("E90001");
        assertLocked(issue, request, "E80001");
    }

    @Test
    void saveDraftAllowedInPendingRectify() {
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_RECTIFY, "E30001");
        issue.setLockVersion(5);
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(issueMapper.updateWorkflowFields(any(CpsIssue.class))).thenReturn(1);
        CpsIssueActionRequest request = action(CpsIssueAction.SAVE_DRAFT);
        request.setReasonAnalysis("draft reason");

        CpsIssueActionResponse response = service.executeAction(900L, request, "E30001");

        assertEquals(CpsIssueStatus.PENDING_RECTIFY, response.getStatus());
        // 乐观锁版本原样透传给 CAS 更新（AC-24）
        ArgumentCaptor<CpsIssue> captor = ArgumentCaptor.forClass(CpsIssue.class);
        verify(issueMapper).updateWorkflowFields(captor.capture());
        assertEquals(Integer.valueOf(5), captor.getValue().getLockVersion());
        assertEquals("draft reason", captor.getValue().getReasonAnalysis());
    }

    @Test
    void transferInPendingFeedbackIsFeedbackReassignNotLocked() {
        // §28.3 锁的是整改转办；PENDING_FEEDBACK+TRANSFER 是反馈指派转交，不受版本锁约束
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_FEEDBACK, "E20001");
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(issueMapper.updateWorkflowFields(any(CpsIssue.class))).thenReturn(1);
        CpsIssueActionRequest request = action(CpsIssueAction.TRANSFER);
        request.setTargetEmpNo("E20002");

        service.executeAction(900L, request, "E20001");

        ArgumentCaptor<CpsIssue> captor = ArgumentCaptor.forClass(CpsIssue.class);
        verify(issueMapper).updateWorkflowFields(captor.capture());
        assertEquals("E20002", captor.getValue().getCurrentHandlerEmpNo());
        verify(transferMapper).insert(any(com.company.cps.domain.CpsRectificationTransfer.class));
    }

    // ---------- AC-25 审核员改配 ----------

    @Test
    void reassignReviewerInPendingReviewMovesHandlerAndKeepsAnchor() {
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_REVIEW, "E80001");
        issue.setReviewerEmpNo("E80001");
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(issueMapper.updateWorkflowFields(any(CpsIssue.class))).thenReturn(1);

        CpsIssueActionResponse response = service.reassignReviewer(900L, "E88001", "E00001", "管理员改配");

        assertEquals(CpsIssueStatus.PENDING_REVIEW, response.getStatus());
        // 原审核员失权：办理权随改配切换到新审核员
        assertEquals("E88001", response.getCurrentHandlerEmpNo());
        ArgumentCaptor<CpsIssue> issueCaptor = ArgumentCaptor.forClass(CpsIssue.class);
        verify(issueMapper).updateWorkflowFields(issueCaptor.capture());
        assertEquals("E88001", issueCaptor.getValue().getReviewerEmpNo());
        assertEquals("E88001", issueCaptor.getValue().getCurrentHandlerEmpNo());
        // 变更记录：action=REVIEWER_REASSIGN，from/to=原/新审核员，comment=reason
        ArgumentCaptor<CpsIssueFlowLog> logCaptor = ArgumentCaptor.forClass(CpsIssueFlowLog.class);
        verify(flowLogMapper).insert(logCaptor.capture());
        assertEquals(CpsIssueAction.REVIEWER_REASSIGN, logCaptor.getValue().getAction());
        assertEquals("E80001", logCaptor.getValue().getFromHandlerEmpNo());
        assertEquals("E88001", logCaptor.getValue().getToHandlerEmpNo());
        assertEquals("管理员改配", logCaptor.getValue().getComment());
    }

    @Test
    void reassignReviewerDuringAiReviewKeepsHandlerEmptyAndDoesNotTouchTask() {
        // §30.2：AI 初审期间改配不重置计时（不触碰初审任务表），初审中无人可办理
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_AI_REVIEW, null);
        issue.setReviewerEmpNo("E80001");
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(issueMapper.updateWorkflowFields(any(CpsIssue.class))).thenReturn(1);

        service.reassignReviewer(900L, "E88001", "E00001", "初审中改配");

        ArgumentCaptor<CpsIssue> captor = ArgumentCaptor.forClass(CpsIssue.class);
        verify(issueMapper).updateWorkflowFields(captor.capture());
        assertEquals("E88001", captor.getValue().getReviewerEmpNo());
        assertNull(captor.getValue().getCurrentHandlerEmpNo());
        // 不重置初审计时：不触碰初审任务域
        verifyNoInteractions(initialReviewService);
        verifyNoInteractions(submissionMapper);
    }

    @Test
    void reassignReviewerInReviewerConfigContinuesToPendingReview() {
        // AC-25：配置完成后继续流转，不要求重新提交
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_REVIEWER_CONFIG, null);
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(issueMapper.updateWorkflowFields(any(CpsIssue.class))).thenReturn(1);

        CpsIssueActionResponse response = service.reassignReviewer(900L, "E88001", "E00001", "补配审核员");

        assertEquals(CpsIssueStatus.PENDING_REVIEW, response.getStatus());
        assertEquals("E88001", response.getCurrentHandlerEmpNo());
    }

    @Test
    void reassignReviewerRejectsFinishedLegacySameAndBlankInputs() {
        // 已完成审核（CLOSED）不可改配
        CpsIssue closed = v2Issue(CpsIssueStatus.CLOSED, null);
        closed.setReviewerEmpNo("E80001");
        when(issueMapper.findById(900L)).thenReturn(Optional.of(closed));
        assertThrows(IllegalStateException.class,
                () -> service.reassignReviewer(900L, "E88001", "E00001", "r"));

        // 新旧审核员相同
        CpsIssue review = v2Issue(CpsIssueStatus.PENDING_REVIEW, "E80001");
        review.setReviewerEmpNo("E80001");
        when(issueMapper.findById(901L)).thenReturn(Optional.of(review));
        assertThrows(IllegalArgumentException.class,
                () -> service.reassignReviewer(901L, "E80001", "E00001", "r"));

        // reason 必填（留痕）
        CpsIssue review2 = v2Issue(CpsIssueStatus.PENDING_REVIEW, "E80001");
        review2.setReviewerEmpNo("E80001");
        when(issueMapper.findById(902L)).thenReturn(Optional.of(review2));
        assertThrows(IllegalArgumentException.class,
                () -> service.reassignReviewer(902L, "E88001", "E00001", "  "));

        // legacy 旧单不走改配
        CpsIssue legacy = v2Issue(CpsIssueStatus.PENDING_REVIEW, "E80001");
        legacy.setFlowVersion("legacy");
        legacy.setReviewerEmpNo("E80001");
        when(issueMapper.findById(903L)).thenReturn(Optional.of(legacy));
        assertThrows(IllegalArgumentException.class,
                () -> service.reassignReviewer(903L, "E88001", "E00001", "r"));
    }

    // ---------- AC-24 乐观锁 ----------

    @Test
    void concurrentModificationDetectedWhenCasUpdateAffectsZeroRows() {
        CpsIssue issue = v2Issue(CpsIssueStatus.PENDING_RECTIFY, "E30001");
        issue.setLockVersion(5);
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(issueMapper.updateWorkflowFields(any(CpsIssue.class))).thenReturn(0);
        CpsIssueActionRequest request = action(CpsIssueAction.SAVE_DRAFT);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.executeAction(900L, request, "E30001"));
        assertTrue(ex.getMessage().contains("Concurrent modification detected on issue 900"));
    }

    // ---------- 状态机锁定语义（纯机校验） ----------

    @Test
    void rectifyEditableOnlyInPendingRectifyAndLockCoversThreeReviewStates() {
        assertTrue(stateMachineV2.isRectifyEditable(CpsIssueStatus.PENDING_RECTIFY));
        assertFalse(stateMachineV2.isRectifyEditable(CpsIssueStatus.PENDING_FEEDBACK));
        assertFalse(stateMachineV2.isRectifyEditable(CpsIssueStatus.PENDING_AI_REVIEW));

        assertTrue(stateMachineV2.isVersionLocked(CpsIssueStatus.PENDING_AI_REVIEW));
        assertTrue(stateMachineV2.isVersionLocked(CpsIssueStatus.PENDING_REVIEWER_CONFIG));
        assertTrue(stateMachineV2.isVersionLocked(CpsIssueStatus.PENDING_REVIEW));
        assertFalse(stateMachineV2.isVersionLocked(CpsIssueStatus.PENDING_RECTIFY));
        assertFalse(stateMachineV2.isVersionLocked(CpsIssueStatus.PENDING_FEEDBACK));
        assertFalse(stateMachineV2.isVersionLocked(CpsIssueStatus.CLOSED));
    }

    @Test
    void assertRectifyEditableThrowsForEveryEditActionInLockedStates() {
        CpsIssueAction[] editActions = {
                CpsIssueAction.SAVE_DRAFT, CpsIssueAction.SUBMIT_RECTIFICATION, CpsIssueAction.TRANSFER};
        // AI 初审/待配置：无人可办理，无任何人工动作
        for (CpsIssueStatus status : new CpsIssueStatus[]{CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueStatus.PENDING_REVIEWER_CONFIG}) {
            for (CpsIssueAction action : editActions) {
                IllegalStateException ex = assertThrows(IllegalStateException.class,
                        () -> stateMachineV2.assertRectifyEditable(status, action),
                        status + "+" + action + " should be locked");
                assertTrue(ex.getMessage().contains("locked"));
            }
            assertTrue(stateMachineV2.availableActions(status).isEmpty(), status + " should expose no manual actions");
        }
        // 待人工审核：审核员可裁决（REVIEW_CLOSE/REVIEW_REJECT），但整改人无编辑类动作
        for (CpsIssueAction action : editActions) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> stateMachineV2.assertRectifyEditable(CpsIssueStatus.PENDING_REVIEW, action),
                    "PENDING_REVIEW+" + action + " should be locked");
            assertTrue(ex.getMessage().contains("locked"));
        }
        assertFalse(stateMachineV2.availableActions(CpsIssueStatus.PENDING_REVIEW).contains(CpsIssueAction.SAVE_DRAFT));
        assertFalse(stateMachineV2.availableActions(CpsIssueStatus.PENDING_REVIEW).contains(CpsIssueAction.TRANSFER));
        assertFalse(stateMachineV2.availableActions(CpsIssueStatus.PENDING_REVIEW).contains(CpsIssueAction.SUBMIT_RECTIFICATION));
        // 非锁定态不拦截（合法性由转移表校验）
        stateMachineV2.assertRectifyEditable(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.SAVE_DRAFT);
        stateMachineV2.assertRectifyEditable(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.TRANSFER);
    }

    // ---------- helpers ----------

    private void assertLocked(CpsIssue issue, CpsIssueActionRequest request, String currentEmpNo) {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.executeAction(900L, request, currentEmpNo));
        assertTrue(ex.getMessage().contains("locked"), "unexpected message: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(issue.getStatus().name()));
        verify(issueMapper, org.mockito.Mockito.never()).updateWorkflowFields(any(CpsIssue.class));
    }

    private static CpsIssueActionRequest action(CpsIssueAction action) {
        CpsIssueActionRequest request = new CpsIssueActionRequest();
        request.setAction(action);
        return request;
    }

    private static CpsIssue v2Issue(CpsIssueStatus status, String handlerEmpNo) {
        CpsIssue issue = new CpsIssue();
        issue.setId(900L);
        issue.setFlowVersion("v2");
        issue.setStatus(status);
        issue.setCurrentHandlerEmpNo(handlerEmpNo);
        issue.setFactory("F1");
        issue.setArea("A1");
        return issue;
    }
}
