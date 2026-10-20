package com.company.cps.dto;

import java.time.LocalDateTime;

/**
 * 我的分配巡检任务（Phase D）：计划建单后分配给某人的任务，Android 任务列表展示。
 * 含类型元信息（名称/是否分区域）与覆盖区域，客户端据此决定执行入口形态。
 */
public class CpsMyInspectionTaskDto {

    private Long taskId;
    private Long planId;
    private String planTitle;
    /** 任务类型 = 巡检类型编码（或历史枚举 INSPECT_*） */
    private String typeCode;
    private String typeName;
    /** 类型是否分区域（历史枚举类型按分区域处理） */
    private Integer zoneFlag;
    private String title;
    private String targetEmpNo;
    private String targetEmpName;
    private LocalDateTime scheduledAt;
    private String frequency;
    private String acceptanceCriteria;
    private String evidenceRequirement;
    /** 覆盖区域编码（逗号分隔）；空=该类型全部启用区域 */
    private String coverageAreaCodes;
    private String taskStatus;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanTitle() { return planTitle; }
    public void setPlanTitle(String planTitle) { this.planTitle = planTitle; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public Integer getZoneFlag() { return zoneFlag; }
    public void setZoneFlag(Integer zoneFlag) { this.zoneFlag = zoneFlag; }
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
    public String getCoverageAreaCodes() { return coverageAreaCodes; }
    public void setCoverageAreaCodes(String coverageAreaCodes) { this.coverageAreaCodes = coverageAreaCodes; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
}
