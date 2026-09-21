package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsKnowledgeCase {
    private Long id;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String categoryL1Name;
    private String categoryL2Name;
    private Boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String imageUrl;
    private String reason;
    private String measure;
    private String milvusVectorId;
    private Integer vectorRetryCount;
    private LocalDateTime vectorUpdatedAt;
    private String vectorStatus;
    private String vectorErrorMsg;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public Long getCategoryL1Id() {
        return categoryL1Id;
    }

    public void setCategoryL1Id(Long categoryL1Id) {
        this.categoryL1Id = categoryL1Id;
    }

    public Long getCategoryL2Id() {
        return categoryL2Id;
    }

    public void setCategoryL2Id(Long categoryL2Id) {
        this.categoryL2Id = categoryL2Id;
    }

    public String getCategoryL1Name() {
        return categoryL1Name;
    }

    public void setCategoryL1Name(String categoryL1Name) {
        this.categoryL1Name = categoryL1Name;
    }

    public String getCategoryL2Name() {
        return categoryL2Name;
    }

    public void setCategoryL2Name(String categoryL2Name) {
        this.categoryL2Name = categoryL2Name;
    }


    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String v) { imageUrl = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public String getMeasure() { return measure; }
    public void setMeasure(String v) { measure = v; }
    public String getMilvusVectorId() { return milvusVectorId; }
    public void setMilvusVectorId(String v) { milvusVectorId = v; }
    public Integer getVectorRetryCount() { return vectorRetryCount; }
    public void setVectorRetryCount(Integer v) { vectorRetryCount = v; }
    public LocalDateTime getVectorUpdatedAt() { return vectorUpdatedAt; }
    public void setVectorUpdatedAt(LocalDateTime v) { vectorUpdatedAt = v; }
    public String getVectorStatus() { return vectorStatus; }
    public void setVectorStatus(String v) { vectorStatus = v; }
    public String getVectorErrorMsg() { return vectorErrorMsg; }
    public void setVectorErrorMsg(String v) { vectorErrorMsg = v; }
}
