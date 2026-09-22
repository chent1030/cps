package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsInspectionPlanTask {
    private Long id;
    private Long planId;
    private CpsInspectionPlanTaskType taskType;
    private String title;
    private String targetEmpNo;
    private String targetEmpName;
    private LocalDateTime scheduledAt;
    private String frequency;
    private String acceptanceCriteria;
    private String evidenceRequirement;
    private String taskStatus;
    private Long referenceIssueId;
    private String referenceObjectKey;
    private String referenceObjectType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public CpsInspectionPlanTaskType getTaskType() { return taskType; }
    public void setTaskType(CpsInspectionPlanTaskType taskType) { this.taskType = taskType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTargetEmpNo() { return targetEmpNo; }
    public void setTargetEmpNo(String targetEmpNo) { this.targetEmpNo = targetEmpNo; }
    public String getTargetEmpName() { return targetEmpName; }
    public void setTargetEmpName(String targetEmpName) { this.targetEmpName = targetEmpName; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
    public String getEvidenceRequirement() { return evidenceRequirement; }
    public void setEvidenceRequirement(String evidenceRequirement) { this.evidenceRequirement = evidenceRequirement; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public Long getReferenceIssueId() { return referenceIssueId; }
    public void setReferenceIssueId(Long referenceIssueId) { this.referenceIssueId = referenceIssueId; }
    public String getReferenceObjectKey() { return referenceObjectKey; }
    public void setReferenceObjectKey(String referenceObjectKey) { this.referenceObjectKey = referenceObjectKey; }
    public String getReferenceObjectType() { return referenceObjectType; }
    public void setReferenceObjectType(String referenceObjectType) { this.referenceObjectType = referenceObjectType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
