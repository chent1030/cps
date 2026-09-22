package com.company.cps.domain;

import java.time.LocalDateTime;

/** 整改提交版本（cps_rectification_submission）：提交即快照+锁定；暂存不生成版本。 */
public class CpsRectificationSubmission {
    private Long id;
    private Long issueId;
    private Integer versionNo;
    private String reason;
    private String shortTermMeasure;
    private String longTermMeasure;
    private String responsibleEmpNo;
    private String responsibleEmpName;
    /** JSON：{"before":[ISSUE阶段附件ID],"after":[PROOF阶段附件ID]}。 */
    private String attachmentIds;
    private String submittedBy;
    private String submittedName;
    private LocalDateTime submittedAt;
    /** SUBMIT / RESUBMIT。 */
    private String source;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getShortTermMeasure() { return shortTermMeasure; }
    public void setShortTermMeasure(String shortTermMeasure) { this.shortTermMeasure = shortTermMeasure; }
    public String getLongTermMeasure() { return longTermMeasure; }
    public void setLongTermMeasure(String longTermMeasure) { this.longTermMeasure = longTermMeasure; }
    public String getResponsibleEmpNo() { return responsibleEmpNo; }
    public void setResponsibleEmpNo(String responsibleEmpNo) { this.responsibleEmpNo = responsibleEmpNo; }
    public String getResponsibleEmpName() { return responsibleEmpName; }
    public void setResponsibleEmpName(String responsibleEmpName) { this.responsibleEmpName = responsibleEmpName; }
    public String getAttachmentIds() { return attachmentIds; }
    public void setAttachmentIds(String attachmentIds) { this.attachmentIds = attachmentIds; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getSubmittedName() { return submittedName; }
    public void setSubmittedName(String submittedName) { this.submittedName = submittedName; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
