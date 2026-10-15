package com.company.cps.dto;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;

import java.time.LocalDateTime;
import java.util.Set;

/** A3 裁决响应：issue 流转结果 + 裁决留痕（duplicated=true 表示该版本已有裁决，幂等返回既有记录）。 */
public class CpsReviewAdjudicationResponse {
    private Long issueId;
    private CpsIssueStatus status;
    private String currentHandlerEmpNo;
    private Set<CpsIssueAction> availableActions;
    private boolean duplicated;
    private Integer versionNo;
    private String decision;
    private String aiOverall;
    private String aiRelation;
    private String reason;
    private LocalDateTime adjudicatedAt;

    public CpsReviewAdjudicationResponse() {
    }

    public CpsReviewAdjudicationResponse(Long issueId, CpsIssueStatus status, String currentHandlerEmpNo,
                                         Set<CpsIssueAction> availableActions, boolean duplicated,
                                         Integer versionNo, String decision, String aiOverall,
                                         String aiRelation, String reason, LocalDateTime adjudicatedAt) {
        this.issueId = issueId;
        this.status = status;
        this.currentHandlerEmpNo = currentHandlerEmpNo;
        this.availableActions = availableActions;
        this.duplicated = duplicated;
        this.versionNo = versionNo;
        this.decision = decision;
        this.aiOverall = aiOverall;
        this.aiRelation = aiRelation;
        this.reason = reason;
        this.adjudicatedAt = adjudicatedAt;
    }

    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public CpsIssueStatus getStatus() { return status; }
    public void setStatus(CpsIssueStatus status) { this.status = status; }
    public String getCurrentHandlerEmpNo() { return currentHandlerEmpNo; }
    public void setCurrentHandlerEmpNo(String currentHandlerEmpNo) { this.currentHandlerEmpNo = currentHandlerEmpNo; }
    public Set<CpsIssueAction> getAvailableActions() { return availableActions; }
    public void setAvailableActions(Set<CpsIssueAction> availableActions) { this.availableActions = availableActions; }
    public boolean isDuplicated() { return duplicated; }
    public void setDuplicated(boolean duplicated) { this.duplicated = duplicated; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getAiOverall() { return aiOverall; }
    public void setAiOverall(String aiOverall) { this.aiOverall = aiOverall; }
    public String getAiRelation() { return aiRelation; }
    public void setAiRelation(String aiRelation) { this.aiRelation = aiRelation; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getAdjudicatedAt() { return adjudicatedAt; }
    public void setAdjudicatedAt(LocalDateTime adjudicatedAt) { this.adjudicatedAt = adjudicatedAt; }
}
