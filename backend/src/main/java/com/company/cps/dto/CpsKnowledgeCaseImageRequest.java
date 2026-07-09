package com.company.cps.dto;

public class CpsKnowledgeCaseImageRequest {
    private Long id;
    private Long caseId;
    private String fileUrl;
    private String fileName;
    private String fileHash;
    private Integer sortNo;
    private String reason;
    private String measure;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getMeasure() { return measure; }
    public void setMeasure(String measure) { this.measure = measure; }
}
