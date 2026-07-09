package com.company.cps.dto;

public class CpsIssueCreateResponse {
    private Long issueId;

    public CpsIssueCreateResponse(Long issueId) {
        this.issueId = issueId;
    }

    public Long getIssueId() { return issueId; }
}
