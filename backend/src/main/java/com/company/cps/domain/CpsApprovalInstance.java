package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * 通用审批实例（Phase A2）。
 * 一条 instance 对应一条业务实体的审批流（bizType + bizId 唯一）。
 */
public class CpsApprovalInstance {
    private Long id;
    private String bizType;
    private Long bizId;
    private String bizTitle;
    private String bizCreatorEmpNo;
    private String bizCreatorEmpName;
    private String bizFactory;
    private String bizArea;
    private CpsApprovalInstanceStatus status;
    private String currentApproverEmpNo;
    private String currentApproverEmpName;
    private LocalDateTime lastActionAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private String rejectReason;
    private String bizSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getBizTitle() { return bizTitle; }
    public void setBizTitle(String bizTitle) { this.bizTitle = bizTitle; }
    public String getBizCreatorEmpNo() { return bizCreatorEmpNo; }
    public void setBizCreatorEmpNo(String bizCreatorEmpNo) { this.bizCreatorEmpNo = bizCreatorEmpNo; }
    public String getBizCreatorEmpName() { return bizCreatorEmpName; }
    public void setBizCreatorEmpName(String bizCreatorEmpName) { this.bizCreatorEmpName = bizCreatorEmpName; }
    public String getBizFactory() { return bizFactory; }
    public void setBizFactory(String bizFactory) { this.bizFactory = bizFactory; }
    public String getBizArea() { return bizArea; }
    public void setBizArea(String bizArea) { this.bizArea = bizArea; }
    public CpsApprovalInstanceStatus getStatus() { return status; }
    public void setStatus(CpsApprovalInstanceStatus status) { this.status = status; }
    public String getCurrentApproverEmpNo() { return currentApproverEmpNo; }
    public void setCurrentApproverEmpNo(String currentApproverEmpNo) { this.currentApproverEmpNo = currentApproverEmpNo; }
    public String getCurrentApproverEmpName() { return currentApproverEmpName; }
    public void setCurrentApproverEmpName(String currentApproverEmpName) { this.currentApproverEmpName = currentApproverEmpName; }
    public LocalDateTime getLastActionAt() { return lastActionAt; }
    public void setLastActionAt(LocalDateTime lastActionAt) { this.lastActionAt = lastActionAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getBizSnapshot() { return bizSnapshot; }
    public void setBizSnapshot(String bizSnapshot) { this.bizSnapshot = bizSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
