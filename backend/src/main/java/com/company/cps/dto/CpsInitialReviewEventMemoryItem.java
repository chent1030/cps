package com.company.cps.dto;

import java.time.LocalDateTime;

/**
 * I 线记忆体系消费：AI 初审事件流水视图（与 cps_initial_review_event 1:1 字段）。
 * 字段命名驼峰直出；记忆体系侧按此 schema 入长期记忆事件轨。
 */
public class CpsInitialReviewEventMemoryItem {
    private Long id;
    private Long taskId;
    private Long issueId;
    private Integer versionNo;
    private String eventType;
    private String detail;
    private String operatorEmpNo;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}