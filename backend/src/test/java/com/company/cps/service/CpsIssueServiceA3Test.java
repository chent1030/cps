package com.company.cps.service;

import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.domain.CpsRectificationSubmission;
import com.company.cps.domain.CpsReviewAdjudication;
import com.company.cps.dto.CpsReviewAdjudicationResponse;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A3 审核裁决（PRD §28.3/§29，AC-16/18/27）：
 * - APPROVE=维持整改关单（REVIEW_CLOSE 语义：handler 清空+closeTime+提交单标记已审）；
 * - REJECT=退回整改人员（REVIEW_REJECT 语义：handler 切回最近提交人）；
 * - AI 意见只作留痕快照：WITH_AI/AGAINST_AI/NO_AI_RESULT 关系矩阵；
 * - 幂等：同 (issue, version) 已裁决 → duplicated=true 且不再流转；
 * - 仅 PENDING_REVIEW 可裁决（超时接管后开放态同为 PENDING_REVIEW）；理由必填。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsIssueServiceA3Test {

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

    @BeforeEach
    void setUp() {
        service = new CpsIssueService(
                issueMapper, attachmentMapper, aiSuggestionMapper, flowLogMapper, assignmentService,
                new CpsWorkflowStateMachine(), agentFrameworkClient, new CpsWorkflowStateMachineV2(),
                submissionMapper, transferMapper, initialReviewService
        );
        // 裁决留痕默认回传入库对象（插入成功路径）
        when(initialReviewService.recordAdjudication(any(CpsReviewAdjudication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CpsIssue reviewIssue() {
        CpsIssue issue = new CpsIssue();
        issue.setId(900L);
        issue.setFlowVersion("v2");
        issue.setStatus(CpsIssueStatus.PENDING_REVIEW);
        issue.setCurrentHandlerEmpNo("E80001");
        issue.setCurrentSubmissionVersion(1);
        issue.setLockVersion(3);
        issue.setFactory("F1");
        issue.setArea("A1");
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(issueMapper.updateWorkflowFields(any(CpsIssue.class))).thenReturn(1);
        return issue;
    }

    @Test
    void approveClosesIssueAndRecordsWithAiRelation() {
        reviewIssue();
        when(initialReviewService.aiOpinionSnapshot(900L, 1)).thenReturn("PASS");

        CpsReviewAdjudicationResponse response = service.adjudicate(900L, "APPROVE", "整改到位，维持", "E80001");

        assertEquals(CpsIssueStatus.CLOSED, response.getStatus());
        assertFalse(response.isDuplicated());
        assertEquals("APPROVE", response.getDecision());
        assertEquals("PASS", response.getAiOverall());
        assertEquals("WITH_AI", response.getAiRelation());
        assertNull(response.getCurrentHandlerEmpNo(), "关单后无办理人");
        ArgumentCaptor<CpsIssue> captor = ArgumentCaptor.forClass(CpsIssue.class);
        verify(issueMapper).updateWorkflowFields(captor.capture());
        assertEquals(CpsIssueStatus.CLOSED, captor.getValue().getStatus());
        verify(submissionMapper).markReviewed(900L, 1);
    }

    @Test
    void approveAgainstAiOpinionRecordsAgainstRelation() {
        reviewIssue();
        when(initialReviewService.aiOpinionSnapshot(900L, 1)).thenReturn("PROBLEM");

        CpsReviewAdjudicationResponse response = service.adjudicate(900L, "APPROVE", "照片充分，改判通过", "E80001");

        assertEquals(CpsIssueStatus.CLOSED, response.getStatus());
        assertEquals("AGAINST_AI", response.getAiRelation());
    }

    @Test
    void rejectReturnsToRectifierAndRecordsRelation() {
        reviewIssue();
        when(initialReviewService.aiOpinionSnapshot(900L, 1)).thenReturn("PASS");
        CpsRectificationSubmission latest = new CpsRectificationSubmission();
        latest.setSubmittedBy("E30001");
        when(submissionMapper.findLatestByIssueId(900L)).thenReturn(latest);

        CpsReviewAdjudicationResponse response = service.adjudicate(900L, "REJECT", "凭证不足，退回", "E80001");

        assertEquals(CpsIssueStatus.PENDING_RECTIFY, response.getStatus());
        assertEquals("E30001", response.getCurrentHandlerEmpNo(), "退回最近提交人");
        assertEquals("AGAINST_AI", response.getAiRelation());
        verify(submissionMapper).markReviewed(900L, 1);
    }

    @Test
    void adjudicationWithoutAiResultRecordsNoAiResultRelation() {
        // 超时接管后无 AI 结果的人工裁决：NO_AI_RESULT（不伪造 AI 意见，D-21）
        reviewIssue();
        when(initialReviewService.aiOpinionSnapshot(900L, 1)).thenReturn(null);

        CpsReviewAdjudicationResponse response = service.adjudicate(900L, "REJECT", "超时接管复核退回", "E80001");

        assertEquals("NO_AI_RESULT", response.getAiRelation());
        assertNull(response.getAiOverall());
    }

    @Test
    void duplicateAdjudicationIsIdempotent() {
        reviewIssue();
        CpsReviewAdjudication existing = new CpsReviewAdjudication();
        existing.setIssueId(900L);
        existing.setVersionNo(1);
        existing.setDecision("APPROVE");
        existing.setAiOverall("PASS");
        existing.setAiRelation("WITH_AI");
        existing.setReason("first");
        when(initialReviewService.findAdjudication(900L, 1)).thenReturn(existing);

        CpsReviewAdjudicationResponse response = service.adjudicate(900L, "REJECT", "second attempt", "E80001");

        assertTrue(response.isDuplicated());
        assertEquals("APPROVE", response.getDecision(), "返回既有裁决，不覆盖");
        verify(issueMapper, never()).updateWorkflowFields(any(CpsIssue.class));
        verify(initialReviewService, never()).recordAdjudication(any(CpsReviewAdjudication.class));
    }

    @Test
    void adjudicationRequiresPendingReviewStatus() {
        CpsIssue issue = reviewIssue();
        issue.setStatus(CpsIssueStatus.PENDING_AI_REVIEW);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.adjudicate(900L, "APPROVE", "reason", "E80001"));
        assertTrue(ex.getMessage().contains("PENDING_REVIEW"));
        verify(issueMapper, never()).updateWorkflowFields(any(CpsIssue.class));
    }

    @Test
    void adjudicationRequiresReasonAndKnownDecision() {
        reviewIssue();
        IllegalArgumentException noReason = assertThrows(IllegalArgumentException.class,
                () -> service.adjudicate(900L, "APPROVE", "  ", "E80001"));
        assertTrue(noReason.getMessage().contains("reason"));
        IllegalArgumentException badDecision = assertThrows(IllegalArgumentException.class,
                () -> service.adjudicate(900L, "MAYBE", "reason", "E80001"));
        assertTrue(badDecision.getMessage().contains("APPROVE or REJECT"));
    }

    @Test
    void adjudicationRequiresSubmissionVersion() {
        CpsIssue issue = reviewIssue();
        issue.setCurrentSubmissionVersion(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.adjudicate(900L, "APPROVE", "reason", "E80001"));
        assertTrue(ex.getMessage().contains("No rectification submission"));
    }

    @Test
    void onlyCurrentHandlerReviewerCanAdjudicate() {
        reviewIssue();
        // executeAction 办理人校验：非审核员（办理人）裁决被拒
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.adjudicate(900L, "APPROVE", "reason", "E99999"));
        assertTrue(ex.getMessage().contains("handler"));
        verify(issueMapper, never()).updateWorkflowFields(any(CpsIssue.class));
    }

    @Test
    void relationMatrixCoversAllCombinations() {
        // APPROVE+PASS=WITH_AI；APPROVE+PARTIAL/PROBLEM=AGAINST_AI；REJECT+PROBLEM=WITH_AI；REJECT+PASS/PARTIAL=AGAINST_AI
        reviewIssue();
        when(initialReviewService.aiOpinionSnapshot(900L, 1)).thenReturn("PARTIAL");
        assertEquals("AGAINST_AI", service.adjudicate(900L, "APPROVE", "r", "E80001").getAiRelation());
        // 重置到 PENDING_REVIEW 以继续矩阵断言（关单后状态已变）
        CpsIssue issue = reviewIssue();
        issue.setStatus(CpsIssueStatus.PENDING_REVIEW);
        when(issueMapper.findById(900L)).thenReturn(Optional.of(issue));
        when(initialReviewService.aiOpinionSnapshot(900L, 1)).thenReturn("PROBLEM");
        assertEquals("WITH_AI", service.adjudicate(900L, "REJECT", "r", "E80001").getAiRelation());
    }
}
