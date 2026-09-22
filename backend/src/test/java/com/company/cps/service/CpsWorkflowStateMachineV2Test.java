package com.company.cps.service;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2 整改域状态机：合法/非法流转、提交落点、系统流转白名单（PRD §28.3 锁定语义）。 */
class CpsWorkflowStateMachineV2Test {

    private final CpsWorkflowStateMachineV2 machine = new CpsWorkflowStateMachineV2();

    @Test
    void legalHumanTransitions() {
        assertEquals(CpsIssueStatus.PENDING_RECTIFY,
                machine.nextStatus(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.REPLY_ASSIGN));
        assertEquals(CpsIssueStatus.PENDING_AI_REVIEW,
                machine.nextStatus(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.SUBMIT_RECTIFICATION));
        assertEquals(CpsIssueStatus.PENDING_RECTIFY,
                machine.nextStatus(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.SAVE_DRAFT));
        assertEquals(CpsIssueStatus.PENDING_RECTIFY,
                machine.nextStatus(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.TRANSFER));
        assertEquals(CpsIssueStatus.CLOSED,
                machine.nextStatus(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.REVIEW_CLOSE));
        assertEquals(CpsIssueStatus.PENDING_RECTIFY,
                machine.nextStatus(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.REVIEW_REJECT));
    }

    @Test
    void illegalTransitionsThrowWithMessage() {
        // 版本锁定：AI 初审中/待配置状态不可人工编辑或转办（PRD §28.3）
        IllegalArgumentException submitDuringReview = assertThrows(IllegalArgumentException.class,
                () -> machine.nextStatus(CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueAction.SUBMIT_RECTIFICATION));
        assertTrue(submitDuringReview.getMessage()
                .contains("Action SUBMIT_RECTIFICATION is not allowed from status PENDING_AI_REVIEW"));

        IllegalArgumentException transferDuringReview = assertThrows(IllegalArgumentException.class,
                () -> machine.nextStatus(CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueAction.TRANSFER));
        assertTrue(transferDuringReview.getMessage().contains("is not allowed"));

        IllegalArgumentException legacyUploadProof = assertThrows(IllegalArgumentException.class,
                () -> machine.nextStatus(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.UPLOAD_PROOF));
        assertTrue(legacyUploadProof.getMessage().contains("is not allowed"));

        IllegalArgumentException closeFromRectify = assertThrows(IllegalArgumentException.class,
                () -> machine.nextStatus(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.REVIEW_CLOSE));
        assertTrue(closeFromRectify.getMessage().contains("is not allowed"));
    }

    @Test
    void lockedStatesExposeNoHumanActions() {
        assertTrue(machine.availableActions(CpsIssueStatus.PENDING_AI_REVIEW).isEmpty());
        assertTrue(machine.availableActions(CpsIssueStatus.PENDING_REVIEWER_CONFIG).isEmpty());
        assertTrue(machine.availableActions(CpsIssueStatus.CLOSED).isEmpty());
    }

    @Test
    void submissionTargetDependsOnReviewerResolution() {
        assertEquals(CpsIssueStatus.PENDING_AI_REVIEW, machine.resolveSubmissionTarget(true));
        // 无审核员可解析：提交保留进入待配置（AC-25），不丢失提交
        assertEquals(CpsIssueStatus.PENDING_REVIEWER_CONFIG, machine.resolveSubmissionTarget(false));
    }

    @Test
    void systemTransitionsWhitelisted() {
        machine.assertSystemTransition(CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueStatus.PENDING_REVIEW);
        machine.assertSystemTransition(CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueStatus.PENDING_REVIEWER_CONFIG);
        machine.assertSystemTransition(CpsIssueStatus.PENDING_REVIEWER_CONFIG, CpsIssueStatus.PENDING_REVIEW);
        assertThrows(IllegalArgumentException.class,
                () -> machine.assertSystemTransition(CpsIssueStatus.PENDING_AI_REVIEW, CpsIssueStatus.CLOSED));
        assertThrows(IllegalArgumentException.class,
                () -> machine.assertSystemTransition(CpsIssueStatus.PENDING_RECTIFY, CpsIssueStatus.PENDING_REVIEW));
    }

    @Test
    void managesCoversV2StatusesOnly() {
        assertTrue(machine.manages(CpsIssueStatus.PENDING_FEEDBACK));
        assertTrue(machine.manages(CpsIssueStatus.PENDING_RECTIFY));
        assertTrue(machine.manages(CpsIssueStatus.PENDING_AI_REVIEW));
        assertTrue(machine.manages(CpsIssueStatus.PENDING_REVIEW));
        assertTrue(machine.manages(CpsIssueStatus.PENDING_REVIEWER_CONFIG));
        assertTrue(machine.manages(CpsIssueStatus.CLOSED));
        // PENDING_UPLOAD_PROOF 为 legacy 专用态（V2 已收敛凭证上传节点），V2 机器不管理
        org.junit.jupiter.api.Assertions.assertFalse(machine.manages(CpsIssueStatus.PENDING_UPLOAD_PROOF));
    }
}
