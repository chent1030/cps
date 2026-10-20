package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * 统一巡检记录明细（Phase C1）：记录 × 点检项，含执行结果快照与判定留痕。
 */
public class CpsInspectionRecordItem {

    private Long id;
    private Long recordId;
    private Long checkItemId;
    private String itemCode;
    private String content;
    /** 点检类型快照（逗号分隔）：PASS_FAIL/TEXT/PHOTO */
    private String checkTypes;
    private Integer photoMinCount;
    private String photoCriteria;
    private Integer deductScore;
    private Integer configVersion;
    /** 是否合格：PASS/FAIL（checkTypes 含 PASS_FAIL） */
    private String passResult;
    /** 文本说明（checkTypes 含 TEXT） */
    private String textValue;
    /** 照片 object_key（多张逗号分隔） */
    private String photoObjectKey;
    private String photoFileName;
    /** 实际拍照张数 */
    private Integer photoCount;
    /** AI 判定：PASS/FAIL/TYPE_MISMATCH/UNJUDGEABLE/PENDING */
    private String judgeResult;
    private String judgeReason;
    /** 最终计分结果 */
    private String finalResult;
    private String finalModifiedBy;
    private LocalDateTime finalModifiedAt;
    private String finalModifyReason;
    private LocalDateTime judgedAt;
    /** 迁移来源 cps_room_check_record_item.id */
    private Long sourceItemId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getCheckItemId() { return checkItemId; }
    public void setCheckItemId(Long checkItemId) { this.checkItemId = checkItemId; }
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
    public Integer getDeductScore() { return deductScore; }
    public void setDeductScore(Integer deductScore) { this.deductScore = deductScore; }
    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }
    public String getPassResult() { return passResult; }
    public void setPassResult(String passResult) { this.passResult = passResult; }
    public String getTextValue() { return textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }
    public String getPhotoObjectKey() { return photoObjectKey; }
    public void setPhotoObjectKey(String photoObjectKey) { this.photoObjectKey = photoObjectKey; }
    public String getPhotoFileName() { return photoFileName; }
    public void setPhotoFileName(String photoFileName) { this.photoFileName = photoFileName; }
    public Integer getPhotoCount() { return photoCount; }
    public void setPhotoCount(Integer photoCount) { this.photoCount = photoCount; }
    public String getJudgeResult() { return judgeResult; }
    public void setJudgeResult(String judgeResult) { this.judgeResult = judgeResult; }
    public String getJudgeReason() { return judgeReason; }
    public void setJudgeReason(String judgeReason) { this.judgeReason = judgeReason; }
    public String getFinalResult() { return finalResult; }
    public void setFinalResult(String finalResult) { this.finalResult = finalResult; }
    public String getFinalModifiedBy() { return finalModifiedBy; }
    public void setFinalModifiedBy(String finalModifiedBy) { this.finalModifiedBy = finalModifiedBy; }
    public LocalDateTime getFinalModifiedAt() { return finalModifiedAt; }
    public void setFinalModifiedAt(LocalDateTime finalModifiedAt) { this.finalModifiedAt = finalModifiedAt; }
    public String getFinalModifyReason() { return finalModifyReason; }
    public void setFinalModifyReason(String finalModifyReason) { this.finalModifyReason = finalModifyReason; }
    public LocalDateTime getJudgedAt() { return judgedAt; }
    public void setJudgedAt(LocalDateTime judgedAt) { this.judgedAt = judgedAt; }
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long sourceItemId) { this.sourceItemId = sourceItemId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
