package com.catlbattery.domain.entity.cps;

import java.time.LocalDateTime;

/**
 * 巡检记录流转日志（非 CPS 复核/整改闭环）：
 * START/SUBMIT/REVIEW_PASS/REVIEW_RETURN/RECTIFY_SUBMIT。
 */
public class CpsInspectionRecordLog {

    private Long id;
    private Long recordId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String operatorEmpNo;
    private String operatorEmpName;
    private String comment;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public String getOperatorEmpName() { return operatorEmpName; }
    public void setOperatorEmpName(String operatorEmpName) { this.operatorEmpName = operatorEmpName; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
