package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsIssueAttachment {
    private Long id;
    private Long issueId;
    private CpsAttachmentStage stage;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private Integer sortNo;
    private String createdBy;
    private String createdName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public CpsAttachmentStage getStage() { return stage; }
    public void setStage(CpsAttachmentStage stage) { this.stage = stage; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedName() { return createdName; }
    public void setCreatedName(String createdName) { this.createdName = createdName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
