package com.company.cps.service;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpsWorkflowStateMachineTest {

    private final CpsWorkflowStateMachine stateMachine = new CpsWorkflowStateMachine();

    @Test
    void pendingFeedbackAllowsReplyAssignAndTransfer() {
        Set<CpsIssueAction> actions = stateMachine.availableActions(CpsIssueStatus.PENDING_FEEDBACK);

        assertEquals(new HashSet<>(Arrays.asList(CpsIssueAction.REPLY_ASSIGN, CpsIssueAction.TRANSFER)), actions);
    }

    @Test
    void replyAssignMovesToPendingRectify() {
        CpsIssueStatus next = stateMachine.nextStatus(
                CpsIssueStatus.PENDING_FEEDBACK,
                CpsIssueAction.REPLY_ASSIGN
        );

        assertEquals(CpsIssueStatus.PENDING_RECTIFY, next);
    }

    @Test
    void reviewRejectMovesBackToUploadProof() {
        CpsIssueStatus next = stateMachine.nextStatus(
                CpsIssueStatus.PENDING_REVIEW,
                CpsIssueAction.REVIEW_REJECT
        );

        assertEquals(CpsIssueStatus.PENDING_UPLOAD_PROOF, next);
    }

    @Test
    void closedStatusAllowsNoActions() {
        assertTrue(stateMachine.availableActions(CpsIssueStatus.CLOSED).isEmpty());
    }

    @Test
    void invalidActionThrows() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> stateMachine.nextStatus(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.REVIEW_CLOSE)
        );

        assertTrue(error.getMessage().contains("Action REVIEW_CLOSE is not allowed"));
    }
}
