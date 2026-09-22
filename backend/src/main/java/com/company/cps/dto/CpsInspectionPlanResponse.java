package com.company.cps.dto;

import com.company.cps.domain.CpsInspectionPlan;
import com.company.cps.domain.CpsInspectionPlanStatus;
import com.company.cps.domain.CpsInspectionPlanTask;
import com.company.cps.domain.CpsInspectionPlanTaskType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CpsInspectionPlanResponse {
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
    private Integer configVersion;
    private Integer lockVersion;
    private String draftBy;
    private String approver;
    private String approverName;
    private LocalDateTime approvedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TaskItem> tasks;

    public static CpsInspectionPlanResponse from(CpsInspectionPlan plan, List<CpsInspectionPlanTask> tasks) {
        CpsInspectionPlanResponse response = new CpsInspectionPlanResponse();
        response.id = plan.getId();
        response.sourceRunId = plan.getSourceRunId();
        response.reportId = plan.getReportId();
        response.planType = plan.getPlanType();
        response.title = plan.getTitle();
        response.targetFactory = plan.getTargetFactory();
        response.targetArea = plan.getTargetArea();
        response.riskBasis = plan.getRiskBasis();
        response.draftContentJson = plan.getDraftContentJson();
        response.status = plan.getStatus();
        response.configVersion = plan.getConfigVersion();
        response.lockVersion = plan.getLockVersion();
        response.draftBy = plan.getDraftBy();
        response.approver = plan.getApprover();
        response.approverName = plan.getApproverName();
        response.approvedAt = plan.getApprovedAt();
        response.rejectReason = plan.getRejectReason();
        response.createdAt = plan.getCreatedAt();
        response.updatedAt = plan.getUpdatedAt();
        response.tasks = tasks == null ? Collections.emptyList() : tasks.stream()
                .map(t -> {
                    TaskItem item = new TaskItem();
                    item.id = t.getId();
                    item.taskType = t.getTaskType();
                    item.title = t.getTitle();
                    item.targetEmpNo = t.getTargetEmpNo();
                    item.targetEmpName = t.getTargetEmpName();
                    item.scheduledAt = t.getScheduledAt();
                    item.frequency = t.getFrequency();
                    item.acceptanceCriteria = t.getAcceptanceCriteria();
                    item.evidenceRequirement = t.getEvidenceRequirement();
                    item.taskStatus = t.getTaskStatus();
                    item.referenceIssueId = t.getReferenceIssueId();
                    item.referenceObjectKey = t.getReferenceObjectKey();
                    item.referenceObjectType = t.getReferenceObjectType();
                    item.createdAt = t.getCreatedAt();
                    return item;
                }).collect(Collectors.toList());
        return response;
    }

    public Long getId() { return id; }
    public String getSourceRunId() { return sourceRunId; }
    public String getReportId() { return reportId; }
    public String getPlanType() { return planType; }
    public String getTitle() { return title; }
    public String getTargetFactory() { return targetFactory; }
    public String getTargetArea() { return targetArea; }
    public String getRiskBasis() { return riskBasis; }
    public String getDraftContentJson() { return draftContentJson; }
    public CpsInspectionPlanStatus getStatus() { return status; }
    public Integer getConfigVersion() { return configVersion; }
    public Integer getLockVersion() { return lockVersion; }
    public String getDraftBy() { return draftBy; }
    public String getApprover() { return approver; }
    public String getApproverName() { return approverName; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public String getRejectReason() { return rejectReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<TaskItem> getTasks() { return tasks; }

    public static class TaskItem {
        private Long id;
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

        public Long getId() { return id; }
        public CpsInspectionPlanTaskType getTaskType() { return taskType; }
        public String getTitle() { return title; }
        public String getTargetEmpNo() { return targetEmpNo; }
        public String getTargetEmpName() { return targetEmpName; }
        public LocalDateTime getScheduledAt() { return scheduledAt; }
        public String getFrequency() { return frequency; }
        public String getAcceptanceCriteria() { return acceptanceCriteria; }
        public String getEvidenceRequirement() { return evidenceRequirement; }
        public String getTaskStatus() { return taskStatus; }
        public Long getReferenceIssueId() { return referenceIssueId; }
        public String getReferenceObjectKey() { return referenceObjectKey; }
        public String getReferenceObjectType() { return referenceObjectType; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}
