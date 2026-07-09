package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsKnowledgeCaseImage {
    private Long id;
    private Long caseId;
    private String fileUrl;
    private String fileName;
    private String fileHash;
    private String milvusVectorId;
    private Integer embeddingDim;
    private CpsVectorStatus vectorStatus;
    private String vectorErrorMsg;
    private Integer vectorRetryCount;
    private LocalDateTime vectorUpdatedAt;
    private Integer sortNo;
    private String reason;
    private String measure;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getMilvusVectorId() {
        return milvusVectorId;
    }

    public void setMilvusVectorId(String milvusVectorId) {
        this.milvusVectorId = milvusVectorId;
    }

    public Integer getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(Integer embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public CpsVectorStatus getVectorStatus() {
        return vectorStatus;
    }

    public void setVectorStatus(CpsVectorStatus vectorStatus) {
        this.vectorStatus = vectorStatus;
    }

    public String getVectorErrorMsg() {
        return vectorErrorMsg;
    }

    public void setVectorErrorMsg(String vectorErrorMsg) {
        this.vectorErrorMsg = vectorErrorMsg;
    }

    public Integer getVectorRetryCount() {
        return vectorRetryCount;
    }

    public void setVectorRetryCount(Integer vectorRetryCount) {
        this.vectorRetryCount = vectorRetryCount;
    }

    public LocalDateTime getVectorUpdatedAt() {
        return vectorUpdatedAt;
    }

    public void setVectorUpdatedAt(LocalDateTime vectorUpdatedAt) {
        this.vectorUpdatedAt = vectorUpdatedAt;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMeasure() {
        return measure;
    }

    public void setMeasure(String measure) {
        this.measure = measure;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
