package com.catlbattery.domain.entity.cps.dto;

public class CpsCheckItemRequest {
    private String itemCode;
    private String content;
    /** 点检类型多选（逗号分隔）：PASS_FAIL/TEXT/PHOTO（Phase C1） */
    private String checkTypes;
    /** 需拍照时最低张数（Phase C1） */
    private Integer photoMinCount;
    /** 拍照判定条件（自由文本）（Phase C1） */
    private String photoCriteria;
    /** 文本框提示语（Phase C1） */
    private String textHint;
    private String photoCategory;
    private Integer deductScore;
    private String status;
    private String applicableRoomTypes;
    private Boolean enabled;

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
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
