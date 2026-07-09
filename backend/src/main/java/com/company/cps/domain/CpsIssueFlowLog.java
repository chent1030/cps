package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsIssueFlowLog {
    private Long id;
    private Long issueId;
    private CpsIssueStatus fromStatus;
    private CpsIssueStatus toStatus;
    private CpsIssueAction action;
    private String operatorEmpNo;
    private String fromHandlerEmpNo;
    private String toHandlerEmpNo;
    private String operatorEmpName;
    private String fromHandlerEmpName;
    private String toHandlerEmpName;
    private String comment;
    private String snapshotJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public CpsIssueStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(CpsIssueStatus fromStatus) { this.fromStatus = fromStatus; }
    public CpsIssueStatus getToStatus() { return toStatus; }
    public void setToStatus(CpsIssueStatus toStatus) { this.toStatus = toStatus; }
    public CpsIssueAction getAction() { return action; }
    public void setAction(CpsIssueAction action) { this.action = action; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public String getFromHandlerEmpNo() { return fromHandlerEmpNo; }
    public void setFromHandlerEmpNo(String fromHandlerEmpNo) { this.fromHandlerEmpNo = fromHandlerEmpNo; }
    public String getToHandlerEmpNo() { return toHandlerEmpNo; }
    public void setToHandlerEmpNo(String toHandlerEmpNo) { this.toHandlerEmpNo = toHandlerEmpNo; }
    public String getOperatorEmpName() { return operatorEmpName; }
    public void setOperatorEmpName(String operatorEmpName) { this.operatorEmpName = operatorEmpName; }
    public String getFromHandlerEmpName() { return fromHandlerEmpName; }
    public void setFromHandlerEmpName(String fromHandlerEmpName) { this.fromHandlerEmpName = fromHandlerEmpName; }
    public String getToHandlerEmpName() { return toHandlerEmpName; }
    public void setToHandlerEmpName(String toHandlerEmpName) { this.toHandlerEmpName = toHandlerEmpName; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
