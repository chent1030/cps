package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * B6 视觉点检记录主表（cps_vision_check_record）。
 * 一房间一检查项一次视觉点检一条；状态机
 * PENDING → AI_JUDGING → AI_PASS/AI_FAIL → HUMAN_OVERRIDE；TIMEOUT 分支。
 * 与 cps_room_check_record（B3 辅房点检执行域，状态机 PENDING/IN_PROGRESS/JUDGED）
 * 业务不同，单独建表避免耦合。
 */
public class CpsVisionCheckRecord {

    public static final String ROOM_TYPE_PRIMARY = "PRIMARY";
    public static final String ROOM_TYPE_STANDARD = "STANDARD";
    public static final String ROOM_TYPE_SPECIAL = "SPECIAL";
    public static final String ROOM_TYPE_TOOL = "TOOL";
    public static final String ROOM_TYPE_OTHER = "OTHER";

    public static final String AI_OVERALL_PASS = "PASS";
    public static final String AI_OVERALL_PARTIAL = "PARTIAL";
    public static final String AI_OVERALL_PROBLEM = "PROBLEM";

    private Long id;
    private Long roomId;
    private Long checkItemId;
    private String roomType;
    private String status;
    private String aiOverall;
    private Integer aiScore;
    private String aiReason;
    private String photoObjectKey;
    private String photoUrl;
    private String judgeFingerprint;
    private String humanOverrideEmpNo;
    private String humanOverrideReason;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getCheckItemId() { return checkItemId; }
    public void setCheckItemId(Long checkItemId) { this.checkItemId = checkItemId; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAiOverall() { return aiOverall; }
    public void setAiOverall(String aiOverall) { this.aiOverall = aiOverall; }
    public Integer getAiScore() { return aiScore; }
    public void setAiScore(Integer aiScore) { this.aiScore = aiScore; }
    public String getAiReason() { return aiReason; }
    public void setAiReason(String aiReason) { this.aiReason = aiReason; }
    public String getPhotoObjectKey() { return photoObjectKey; }
    public void setPhotoObjectKey(String photoObjectKey) { this.photoObjectKey = photoObjectKey; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getJudgeFingerprint() { return judgeFingerprint; }
    public void setJudgeFingerprint(String judgeFingerprint) { this.judgeFingerprint = judgeFingerprint; }
    public String getHumanOverrideEmpNo() { return humanOverrideEmpNo; }
    public void setHumanOverrideEmpNo(String humanOverrideEmpNo) { this.humanOverrideEmpNo = humanOverrideEmpNo; }
    public String getHumanOverrideReason() { return humanOverrideReason; }
    public void setHumanOverrideReason(String humanOverrideReason) { this.humanOverrideReason = humanOverrideReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}