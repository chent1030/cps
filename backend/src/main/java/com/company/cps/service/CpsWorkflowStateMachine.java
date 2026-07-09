package com.company.cps.service;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class CpsWorkflowStateMachine {

    private static final Map<CpsIssueStatus, Map<CpsIssueAction, CpsIssueStatus>> TRANSITIONS =
            new EnumMap<>(CpsIssueStatus.class);

    static {
        put(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.REPLY_ASSIGN, CpsIssueStatus.PENDING_RECTIFY);
        put(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_FEEDBACK);
        put(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.RECTIFY, CpsIssueStatus.PENDING_UPLOAD_PROOF);
        put(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_RECTIFY);
        put(CpsIssueStatus.PENDING_UPLOAD_PROOF, CpsIssueAction.UPLOAD_PROOF, CpsIssueStatus.PENDING_REVIEW);
        put(CpsIssueStatus.PENDING_UPLOAD_PROOF, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_UPLOAD_PROOF);
        put(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.REVIEW_CLOSE, CpsIssueStatus.CLOSED);
        put(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.REVIEW_REJECT, CpsIssueStatus.PENDING_UPLOAD_PROOF);
        put(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_REVIEW);
    }

    private static void put(CpsIssueStatus from, CpsIssueAction action, CpsIssueStatus to) {
        TRANSITIONS.computeIfAbsent(from, ignored -> new EnumMap<>(CpsIssueAction.class)).put(action, to);
    }

    public Set<CpsIssueAction> availableActions(CpsIssueStatus status) {
        return TRANSITIONS.getOrDefault(status, Collections.emptyMap()).keySet();
    }

    public CpsIssueStatus nextStatus(CpsIssueStatus status, CpsIssueAction action) {
        CpsIssueStatus next = TRANSITIONS.getOrDefault(status, Collections.emptyMap()).get(action);
        if (next == null) {
            throw new IllegalArgumentException("Action " + action + " is not allowed from status " + status);
        }
        return next;
    }
}
