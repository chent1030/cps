package com.company.cps.domain;

import java.time.LocalDateTime;

/** AI 初审事件流水（cps_initial_review_event）：触发/投递重试/回调/超时/接管/迟到/重触发/裁决全链路留痕。 */
public class CpsInitialReviewEvent {
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
