package com.company.cps.dto;

import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueAiSuggestion;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.domain.CpsIssueFlowLog;

import java.util.List;
import java.util.Set;

public class CpsIssueDetailResponse {
    private CpsIssue issue;
    private List<CpsIssueAttachment> issueAttachments;
    private List<CpsIssueAttachment> proofAttachments;
    private CpsIssueAiSuggestion aiSuggestion;
    private List<CpsIssueFlowLog> flowLogs;
    private Set<CpsIssueAction> availableActions;

    public CpsIssue getIssue() { return issue; }
    public void setIssue(CpsIssue issue) { this.issue = issue; }
    public List<CpsIssueAttachment> getIssueAttachments() { return issueAttachments; }
    public void setIssueAttachments(List<CpsIssueAttachment> issueAttachments) { this.issueAttachments = issueAttachments; }
    public List<CpsIssueAttachment> getProofAttachments() { return proofAttachments; }
    public void setProofAttachments(List<CpsIssueAttachment> proofAttachments) { this.proofAttachments = proofAttachments; }
    public CpsIssueAiSuggestion getAiSuggestion() { return aiSuggestion; }
    public void setAiSuggestion(CpsIssueAiSuggestion aiSuggestion) { this.aiSuggestion = aiSuggestion; }
    public List<CpsIssueFlowLog> getFlowLogs() { return flowLogs; }
    public void setFlowLogs(List<CpsIssueFlowLog> flowLogs) { this.flowLogs = flowLogs; }
    public Set<CpsIssueAction> getAvailableActions() { return availableActions; }
    public void setAvailableActions(Set<CpsIssueAction> availableActions) { this.availableActions = availableActions; }
}
