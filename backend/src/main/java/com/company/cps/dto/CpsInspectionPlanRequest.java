package com.company.cps.dto;

import java.time.LocalDateTime;

public class CpsInspectionPlanRequest {
    private String sourceRunId;
    private String reportId;
    private String planType;
    private String title;
    private String targetFactory;
    private String targetArea;
    private String riskBasis;
    /** 服务端调 C-07 时可由 Python 重新生成；前端可直接预填。 */
    private String draftContentJson;
    /** 可选：人工指定负责人/计划时间（审核页用）。 */
    private String targetEmpNo;
    private String targetEmpName;
    private LocalDateTime scheduledAt;
    private String frequency;
    private String acceptanceCriteria;
    private String evidenceRequirement;
    private String draftBy;

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
    public String getDraftBy() { return draftBy; }
    public void setDraftBy(String draftBy) { this.draftBy = draftBy; }
}
