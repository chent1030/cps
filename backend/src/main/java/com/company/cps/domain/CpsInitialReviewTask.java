package com.company.cps.domain;

import java.time.LocalDateTime;

/** AI 初审任务（cps_initial_review_task）：Java 为唯一状态真相源。 */
public class CpsInitialReviewTask {
    private Long id;
    private Long issueId;
    private Long submissionId;
    private Integer versionNo;
    private CpsInitialReviewTaskStatus status;
    private String reviewTaskRef;
    private String idempotencyKey;
    private LocalDateTime submittedAt;
    private LocalDateTime timeoutAt;
    private LocalDateTime completedAt;
    private String takenOverBy;
    private String takenOverName;
    private LocalDateTime takenOverAt;
    private String takeoverReason;
    private String errorCode;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public CpsInitialReviewTaskStatus getStatus() { return status; }
    public void setStatus(CpsInitialReviewTaskStatus status) { this.status = status; }
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
    public String getTakenOverBy() { return takenOverBy; }
    public void setTakenOverBy(String takenOverBy) { this.takenOverBy = takenOverBy; }
    public String getTakenOverName() { return takenOverName; }
    public void setTakenOverName(String takenOverName) { this.takenOverName = takenOverName; }
    public LocalDateTime getTakenOverAt() { return takenOverAt; }
    public void setTakenOverAt(LocalDateTime takenOverAt) { this.takenOverAt = takenOverAt; }
    public String getTakeoverReason() { return takeoverReason; }
    public void setTakeoverReason(String takeoverReason) { this.takeoverReason = takeoverReason; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
