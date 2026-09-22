package com.company.cps.domain;

import java.time.LocalDateTime;

/** 整改转办记录（cps_rectification_transfer）：转办后仅当前承办人办理，责任员工不变。 */
public class CpsRectificationTransfer {
    private Long id;
    private Long issueId;
    private Integer versionNo;
    private String fromEmpNo;
    private String fromEmpName;
    private String toEmpNo;
    private String toEmpName;
    private LocalDateTime transferredAt;
    private String remark;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getFromEmpNo() { return fromEmpNo; }
    public void setFromEmpNo(String fromEmpNo) { this.fromEmpNo = fromEmpNo; }
    public String getFromEmpName() { return fromEmpName; }
    public void setFromEmpName(String fromEmpName) { this.fromEmpName = fromEmpName; }
    public String getToEmpNo() { return toEmpNo; }
    public void setToEmpNo(String toEmpNo) { this.toEmpNo = toEmpNo; }
    public String getToEmpName() { return toEmpName; }
    public void setToEmpName(String toEmpName) { this.toEmpName = toEmpName; }
    public LocalDateTime getTransferredAt() { return transferredAt; }
    public void setTransferredAt(LocalDateTime transferredAt) { this.transferredAt = transferredAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
