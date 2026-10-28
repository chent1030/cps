package com.catlbattery.domain.entity.cps;

import java.time.LocalDateTime;

/** B3 辅房点检明细（cps_room_check_record_item）：房间×点检项，配置快照 + 照片 + 判定结果。 */
public class CpsRoomCheckRecordItem {
    private Long id;
    private Long recordId;
    private Long roomId;
    private Long checkItemId;
    private String itemCode;
    private String content;
    private String photoCategory;
    private Integer deductScore;
    private Integer configVersion;
    private String photoUrl;
    private String photoFileName;
    private String judgeResult;
    private String judgeReason;
    private String finalResult;
    private String finalModifiedBy;
    private LocalDateTime finalModifiedAt;
    private String finalModifyReason;
    private LocalDateTime judgedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getCheckItemId() { return checkItemId; }
    public void setCheckItemId(Long checkItemId) { this.checkItemId = checkItemId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPhotoCategory() { return photoCategory; }
    public void setPhotoCategory(String photoCategory) { this.photoCategory = photoCategory; }
    public Integer getDeductScore() { return deductScore; }
    public void setDeductScore(Integer deductScore) { this.deductScore = deductScore; }
    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getPhotoFileName() { return photoFileName; }
    public void setPhotoFileName(String photoFileName) { this.photoFileName = photoFileName; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
