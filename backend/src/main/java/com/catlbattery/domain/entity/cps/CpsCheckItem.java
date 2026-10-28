package com.catlbattery.domain.entity.cps;

import java.time.LocalDateTime;

public class CpsCheckItem {
    private Long id;
    private String itemCode;
    private String content;
    /** 点检类型多选（逗号分隔）：PASS_FAIL是否合格 / TEXT文本框 / PHOTO需拍照（Phase C1） */
    private String checkTypes;
    /** 需拍照时最低张数（checkTypes 含 PHOTO 时必填）（Phase C1） */
    private Integer photoMinCount;
    /** 拍照判定条件（自由文本，供 AI/人工判定参考）（Phase C1） */
    private String photoCriteria;
    /** 文本框提示语（checkTypes 含 TEXT 时展示）（Phase C1） */
    private String textHint;
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
    public String getCheckTypes() { return checkTypes; }
    public void setCheckTypes(String checkTypes) { this.checkTypes = checkTypes; }
    public Integer getPhotoMinCount() { return photoMinCount; }
    public void setPhotoMinCount(Integer photoMinCount) { this.photoMinCount = photoMinCount; }
    public String getPhotoCriteria() { return photoCriteria; }
    public void setPhotoCriteria(String photoCriteria) { this.photoCriteria = photoCriteria; }
    public String getTextHint() { return textHint; }
    public void setTextHint(String textHint) { this.textHint = textHint; }
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
