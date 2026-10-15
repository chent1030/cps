package com.company.cps.dto;

import java.time.LocalDateTime;

/**
 * I 线记忆体系消费：单条裁决视图（与 cps_review_adjudication 1:1 字段）。
 * 字段命名驼峰直出；记忆体系侧（Python basic-project）按此 schema 写入长期记忆。
 */
public class CpsAdjudicationMemoryItem {
    private Long id;
    private Long issueId;
    private Integer versionNo;
    private Long taskId;
    private String reviewerEmpNo;
    private String reviewerEmpName;
    private String decision;
    private String aiOverall;
    private String aiRelation;
    private String reason;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getReviewerEmpNo() { return reviewerEmpNo; }
    public void setReviewerEmpNo(String reviewerEmpNo) { this.reviewerEmpNo = reviewerEmpNo; }
    public String getReviewerEmpName() { return reviewerEmpName; }
    public void setReviewerEmpName(String reviewerEmpName) { this.reviewerEmpName = reviewerEmpName; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getAiOverall() { return aiOverall; }
    public void setAiOverall(String aiOverall) { this.aiOverall = aiOverall; }
    public String getAiRelation() { return aiRelation; }
    public void setAiRelation(String aiRelation) { this.aiRelation = aiRelation; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}