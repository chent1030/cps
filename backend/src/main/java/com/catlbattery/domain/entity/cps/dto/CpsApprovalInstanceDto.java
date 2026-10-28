package com.catlbattery.domain.entity.cps.dto;

import com.catlbattery.domain.entity.cps.CpsApprovalInstance;
import com.catlbattery.domain.entity.cps.CpsApprovalInstanceStatus;

import java.time.LocalDateTime;

/**
 * 审批中心列表/详情 DTO。
 * 包含业务标题、提交人、当前待审人等扁平字段；详情再多带 steps + bizSnapshot。
 */
public class CpsApprovalInstanceDto {
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
    private Integer stepCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CpsApprovalInstanceDto from(CpsApprovalInstance e) {
        CpsApprovalInstanceDto d = new CpsApprovalInstanceDto();
        d.id = e.getId();
        d.bizType = e.getBizType();
        d.bizId = e.getBizId();
        d.bizTitle = e.getBizTitle();
        d.bizCreatorEmpNo = e.getBizCreatorEmpNo();
        d.bizCreatorEmpName = e.getBizCreatorEmpName();
        d.bizFactory = e.getBizFactory();
        d.bizArea = e.getBizArea();
        d.status = e.getStatus();
        d.currentApproverEmpNo = e.getCurrentApproverEmpNo();
        d.currentApproverEmpName = e.getCurrentApproverEmpName();
        d.lastActionAt = e.getLastActionAt();
        d.approvedAt = e.getApprovedAt();
        d.rejectedAt = e.getRejectedAt();
        d.rejectReason = e.getRejectReason();
        d.bizSnapshot = e.getBizSnapshot();
        d.createdAt = e.getCreatedAt();
        d.updatedAt = e.getUpdatedAt();
        return d;
    }

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
    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
