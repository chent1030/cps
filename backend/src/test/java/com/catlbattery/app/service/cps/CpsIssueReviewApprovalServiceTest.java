package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsIssue;
import com.catlbattery.domain.entity.cps.CpsIssueStatus;
import com.catlbattery.domain.entity.cps.CpsRectificationSubmission;
import com.catlbattery.infra.mapper.cps.CpsIssueMapper;
import com.catlbattery.infra.mapper.cps.CpsRectificationSubmissionMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 步骤 2：问题整改审核 → web 审批中心 的桥接口径。
 * 审批动作只在 web 端发生，Android 只读；批准=关单，驳回=退回整改人。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsIssueReviewApprovalServiceTest {

    @Mock
    private CpsApprovalService approvalService;
    @Mock
    private CpsRectificationSubmissionMapper submissionMapper;
    @Mock
    private CpsIssueMapper issueMapper;
    @Mock
    private CpsIssueService issueService;

    private CpsIssueReviewApprovalService service;

    @BeforeEach
    void setUp() {
        service = new CpsIssueReviewApprovalService(approvalService, submissionMapper, issueMapper);
        service.setIssueService(issueService);
    }

    // ---------- 建实例 ----------

    @Test
    void ensurePendingApprovalUsesSubmissionIdAsBizIdAndCarriesAiOpinion() {
        CpsIssue issue = pendingReviewIssue();
        CpsRectificationSubmission submission = submissionOf(issue.getId(), 2);
        when(submissionMapper.findByIssueAndVersion(issue.getId(), 2)).thenReturn(submission);
        when(approvalService.ensureInstance(anyString(), anyLong(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn(77L);

        Long instanceId = service.ensurePendingApproval(issue, "AI 初审通过；短期措施=通过");

        assertEquals(77L, instanceId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> snapshotCaptor = ArgumentCaptor.forClass(Map.class);
        verify(approvalService).ensureInstance(eq(CpsIssueReviewApprovalService.BIZ_TYPE),
                eq(submission.getId()), eq("冷却水温度偏高"), eq("R001"), eq("整改人"),
                eq("一厂"), eq("A 区"), snapshotCaptor.capture());
        Map<String, Object> snapshot = snapshotCaptor.getValue();
        assertEquals("AI 初审通过；短期措施=通过", snapshot.get("AI 初审意见"));
        assertEquals(2, snapshot.get("整改版本"));
        assertEquals("原因：温度高", snapshot.get("原因分析"));
        assertEquals("责任员工(Resp01)", snapshot.get("责任员工"));
        assertEquals("2 张", snapshot.get("整改照片"));
    }

    @Test
    void ensurePendingApprovalSkipsWhenSubmissionMissing() {
        CpsIssue issue = pendingReviewIssue();
        when(submissionMapper.findByIssueAndVersion(issue.getId(), 2)).thenReturn(null);

        assertNull(service.ensurePendingApproval(issue, "AI 初审通过"));
        verifyNoInteractions(approvalService);
    }

    @Test
    void ensurePendingApprovalSkipsWhenNoSubmissionVersion() {
        CpsIssue issue = pendingReviewIssue();
        issue.setCurrentSubmissionVersion(null);

        assertNull(service.ensurePendingApproval(issue, "AI 初审通过"));
        verifyNoInteractions(approvalService);
    }

    @Test
    void ensurePendingApprovalSwallowsSettledInstance() {
        CpsIssue issue = pendingReviewIssue();
        when(submissionMapper.findByIssueAndVersion(issue.getId(), 2)).thenReturn(submissionOf(issue.getId(), 2));
        when(approvalService.ensureInstance(anyString(), anyLong(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenThrow(new IllegalStateException("already settled"));

        assertNull(service.ensurePendingApproval(issue, "AI 初审"));
    }

    // ---------- 审批结果驱动业务 ----------

    @Test
    void handleDecisionApproveClosesIssueAndTakesHandlerOverToApprover() {
        CpsIssue issue = pendingReviewIssue(); // handler = 审核员 600
        when(submissionMapper.findById(31L)).thenReturn(submissionOf(issue.getId(), 2));
        when(issueMapper.findById(issue.getId())).thenReturn(Optional.of(issue));

        boolean handled = service.handleDecision(31L, true, "照片清晰，整改到位", "90001", "审批人");

        assertTrue(handled);
        verify(issueMapper).updateReviewRouting(eq(issue.getId()), eq("90001"), eq("90001"),
                eq(CpsIssueStatus.PENDING_REVIEW), any(LocalDateTime.class));
        verify(issueService).adjudicate(issue.getId(), "APPROVE", "照片清晰，整改到位", "90001");
    }

    @Test
    void handleDecisionRejectFallsBackToDefaultReason() {
        CpsIssue issue = pendingReviewIssue();
        issue.setCurrentHandlerEmpNo("90001"); // 审批人就是当前办理人，不触发改写
        when(submissionMapper.findById(31L)).thenReturn(submissionOf(issue.getId(), 2));
        when(issueMapper.findById(issue.getId())).thenReturn(Optional.of(issue));

        boolean handled = service.handleDecision(31L, false, "  ", "90001", "审批人");

        assertTrue(handled);
        verify(issueMapper, never()).updateReviewRouting(anyLong(), anyString(), anyString(), any(), any());
        verify(issueService).adjudicate(issue.getId(), "REJECT", "审批中心驳回：整改不通过，退回整改人", "90001");
    }

    @Test
    void handleDecisionSkipsWhenIssueAlreadyLeftPendingReview() {
        CpsIssue issue = pendingReviewIssue();
        issue.setStatus(CpsIssueStatus.CLOSED);
        when(submissionMapper.findById(31L)).thenReturn(submissionOf(issue.getId(), 2));
        when(issueMapper.findById(issue.getId())).thenReturn(Optional.of(issue));

        assertFalse(service.handleDecision(31L, true, "ok", "90001", "审批人"));
        verifyNoInteractions(issueService);
    }

    @Test
    void handleDecisionSkipsStaleSubmissionVersion() {
        CpsIssue issue = pendingReviewIssue();
        issue.setCurrentSubmissionVersion(3);
        when(submissionMapper.findById(31L)).thenReturn(submissionOf(issue.getId(), 2));
        when(issueMapper.findById(issue.getId())).thenReturn(Optional.of(issue));

        assertFalse(service.handleDecision(31L, true, "ok", "90001", "审批人"));
        verifyNoInteractions(issueService);
        verify(issueMapper, never()).updateReviewRouting(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void handleDecisionSkipsWhenIssueServiceNotWired() {
        CpsIssueReviewApprovalService bare = new CpsIssueReviewApprovalService(approvalService, submissionMapper, issueMapper);

        assertFalse(bare.handleDecision(31L, true, "ok", "90001", "审批人"));
        verifyNoInteractions(submissionMapper, issueService);
    }

    // ---------- fixtures ----------

    private CpsIssue pendingReviewIssue() {
        CpsIssue issue = new CpsIssue();
        issue.setId(900L);
        issue.setStatus(CpsIssueStatus.PENDING_REVIEW);
        issue.setCurrentSubmissionVersion(2);
        issue.setCurrentHandlerEmpNo("600");
        issue.setCurrentHandlerEmpName("审核员");
        issue.setReviewerEmpNo("600");
        issue.setDescription("冷却水温度偏高");
        issue.setInspectionTypeCode("CPS_INSPECTION");
        issue.setFactory("一厂");
        issue.setArea("A 区");
        issue.setCreatorEmpName("上报人");
        return issue;
    }

    private CpsRectificationSubmission submissionOf(Long issueId, int versionNo) {
        CpsRectificationSubmission submission = new CpsRectificationSubmission();
        submission.setId(31L);
        submission.setIssueId(issueId);
        submission.setVersionNo(versionNo);
        submission.setReason("原因：温度高");
        submission.setShortTermMeasure("短期：调低负载");
        submission.setLongTermMeasure("长期：更换换热器");
        submission.setResponsibleEmpNo("Resp01");
        submission.setResponsibleEmpName("责任员工");
        submission.setAttachmentIds("11,22");
        submission.setSubmittedBy("R001");
        submission.setSubmittedName("整改人");
        submission.setSubmittedAt(LocalDateTime.of(2026, 10, 28, 10, 30));
        return submission;
    }
}
