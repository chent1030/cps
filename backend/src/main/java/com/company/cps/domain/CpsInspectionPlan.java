package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsInspectionPlan {
    private Long id;
    private String sourceRunId;
    private String reportId;
    private String planType;
    private String title;
    private String targetFactory;
    private String targetArea;
    private String riskBasis;
    private String draftContentJson;
    private CpsInspectionPlanStatus status;
    private String draftIdempotencyKey;
    private Integer configVersion;
    private Integer lockVersion;
    private String draftBy;
    private String approver;
    private String approverName;
    private LocalDateTime approvedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceRunId() { return sourceRunId; }
    public void setSourceRunId(String sourceRunId) { this.sourceRunId = sourceRunId; }
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTargetFactory() { return targetFactory; }
    public void setTargetFactory(String targetFactory) { this.targetFactory = targetFactory; }
    public String getTargetArea() { return targetArea; }
    public void setTargetArea(String targetArea) { this.targetArea = targetArea; }
    public String getRiskBasis() { return riskBasis; }
    public void setRiskBasis(String riskBasis) { this.riskBasis = riskBasis; }
    public String getDraftContentJson() { return draftContentJson; }
    public void setDraftContentJson(String draftContentJson) { this.draftContentJson = draftContentJson; }
    public CpsInspectionPlanStatus getStatus() { return status; }
    public void setStatus(CpsInspectionPlanStatus status) { this.status = status; }
    public String getDraftIdempotencyKey() { return draftIdempotencyKey; }
    public void setDraftIdempotencyKey(String draftIdempotencyKey) { this.draftIdempotencyKey = draftIdempotencyKey; }
    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }
    public Integer getLockVersion() { return lockVersion; }
    public void setLockVersion(Integer lockVersion) { this.lockVersion = lockVersion; }
    public String getDraftBy() { return draftBy; }
    public void setDraftBy(String draftBy) { this.draftBy = draftBy; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
