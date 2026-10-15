package com.company.cps.dto;

import java.time.LocalDateTime;

/** A4 admin 触发记录行视图：任务字段 + 问题状态 + 初审结果 + 裁决关联（mapper JOIN 直填）。 */
public class CpsInitialReviewAdminTaskView {
    private Long id;
    private Long issueId;
    private Long submissionId;
    private Integer versionNo;
    private String status;
    private String reviewTaskRef;
    private String idempotencyKey;
    private LocalDateTime submittedAt;
    private LocalDateTime timeoutAt;
    private LocalDateTime completedAt;
    private String errorCode;
    private Integer retryCount;
    private String takenOverBy;
    private String takenOverName;
    private LocalDateTime takenOverAt;
    private String takeoverReason;
    private String issueStatus;
    private String resultOverall;
    private Boolean resultIsLate;
    private String adjudicationDecision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewTaskRef() { return reviewTaskRef; }
    public void setReviewTaskRef(String reviewTaskRef) { this.reviewTaskRef = reviewTaskRef; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getTimeoutAt() { return timeoutAt; }
    public void setTimeoutAt(LocalDateTime timeoutAt) { this.timeoutAt = timeoutAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getTakenOverBy() { return takenOverBy; }
    public void setTakenOverBy(String takenOverBy) { this.takenOverBy = takenOverBy; }
    public String getTakenOverName() { return takenOverName; }
    public void setTakenOverName(String takenOverName) { this.takenOverName = takenOverName; }
    public LocalDateTime getTakenOverAt() { return takenOverAt; }
    public void setTakenOverAt(LocalDateTime takenOverAt) { this.takenOverAt = takenOverAt; }
    public String getTakeoverReason() { return takeoverReason; }
    public void setTakeoverReason(String takeoverReason) { this.takeoverReason = takeoverReason; }
    public String getIssueStatus() { return issueStatus; }
    public void setIssueStatus(String issueStatus) { this.issueStatus = issueStatus; }
    public String getResultOverall() { return resultOverall; }
    public void setResultOverall(String resultOverall) { this.resultOverall = resultOverall; }
    public Boolean getResultIsLate() { return resultIsLate; }
    public void setResultIsLate(Boolean resultIsLate) { this.resultIsLate = resultIsLate; }
    public String getAdjudicationDecision() { return adjudicationDecision; }
    public void setAdjudicationDecision(String adjudicationDecision) { this.adjudicationDecision = adjudicationDecision; }
}
