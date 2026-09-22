package com.company.cps.domain;

import java.time.LocalDateTime;

/** AI 初审结果（cps_initial_review_result）：仅意见，无决定权；一个任务至多一条，迟到结果 is_late 留痕。 */
public class CpsInitialReviewResult {
    private Long id;
    private Long taskId;
    private Long submissionId;
    private Long issueId;
    private Integer versionNo;
    /** PASS / PARTIAL / PROBLEM。 */
    private String overall;
    private String modelStatus;
    private Boolean isLate;
    private String callbackIdempotencyKey;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getOverall() { return overall; }
    public void setOverall(String overall) { this.overall = overall; }
    public String getModelStatus() { return modelStatus; }
    public void setModelStatus(String modelStatus) { this.modelStatus = modelStatus; }
    public Boolean getIsLate() { return isLate; }
    public void setIsLate(Boolean isLate) { this.isLate = isLate; }
    public String getCallbackIdempotencyKey() { return callbackIdempotencyKey; }
    public void setCallbackIdempotencyKey(String callbackIdempotencyKey) { this.callbackIdempotencyKey = callbackIdempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
