package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsCheckItem {
    private Long id;
    private String itemCode;
    private String content;
    private String photoCategory;
    private Integer deductScore;
    private String status;
    private String applicableRoomTypes;
    private Integer configVersion;
    private Boolean enabled;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPhotoCategory() { return photoCategory; }
    public void setPhotoCategory(String photoCategory) { this.photoCategory = photoCategory; }
    public Integer getDeductScore() { return deductScore; }
    public void setDeductScore(Integer deductScore) { this.deductScore = deductScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApplicableRoomTypes() { return applicableRoomTypes; }
    public void setApplicableRoomTypes(String applicableRoomTypes) { this.applicableRoomTypes = applicableRoomTypes; }
    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
