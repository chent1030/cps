package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * 统一巡检记录（Phase C1）：一次执行一条；JUDGED 后锁定。
 * 分区域类型 areaId 必填；不分区域类型为 NULL（按 typeCode 直挂点检项）。
 */
public class CpsInspectionRecord {

    private Long id;
    private String typeCode;
    private Long areaId;
    private String areaCode;
    private String areaName;
    private Long planTaskId;
    private Long planId;
    private String checkEmpNo;
    private String checkEmpName;
    /** PENDING/IN_PROGRESS/AWAIT_CONFIRM/JUDGED */
    private String recordStatus;
    /** AI判定服务状态：SUCCESS/PENDING（降级） */
    private String judgeStatus;
    private Integer score;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    /** 迁移来源 cps_room_check_record.id */
    private Long sourceRecordId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public Long getPlanTaskId() { return planTaskId; }
    public void setPlanTaskId(Long planTaskId) { this.planTaskId = planTaskId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getCheckEmpNo() { return checkEmpNo; }
    public void setCheckEmpNo(String checkEmpNo) { this.checkEmpNo = checkEmpNo; }
    public String getCheckEmpName() { return checkEmpName; }
    public void setCheckEmpName(String checkEmpName) { this.checkEmpName = checkEmpName; }
    public String getRecordStatus() { return recordStatus; }
    public void setRecordStatus(String recordStatus) { this.recordStatus = recordStatus; }
    public String getJudgeStatus() { return judgeStatus; }
    public void setJudgeStatus(String judgeStatus) { this.judgeStatus = judgeStatus; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
