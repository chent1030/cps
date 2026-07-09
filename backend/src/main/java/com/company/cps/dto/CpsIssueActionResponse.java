package com.company.cps.dto;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;

import java.util.Set;

public class CpsIssueActionResponse {
    private Long issueId;
    private CpsIssueStatus status;
    private String currentHandlerEmpNo;
    private Set<CpsIssueAction> availableActions;

    public CpsIssueActionResponse(Long issueId, CpsIssueStatus status, String currentHandlerEmpNo, Set<CpsIssueAction> availableActions) {
        this.issueId = issueId;
        this.status = status;
        this.currentHandlerEmpNo = currentHandlerEmpNo;
        this.availableActions = availableActions;
    }

    public Long getIssueId() { return issueId; }
    public CpsIssueStatus getStatus() { return status; }
    public String getCurrentHandlerEmpNo() { return currentHandlerEmpNo; }
    public Set<CpsIssueAction> getAvailableActions() { return availableActions; }
}
