package com.company.cps.domain;

import java.time.LocalDateTime;

/** B3 辅房点检单（cps_room_check_record）：一房间一次点检一条。 */
public class CpsRoomCheckRecord {
    private Long id;
    private Long planTaskId;
    private Long planId;
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String checkEmpNo;
    private String checkEmpName;
    private String recordStatus;
    private String judgeStatus;
    private Integer judgeAttempt;
    private Integer score;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanTaskId() { return planTaskId; }
    public void setPlanTaskId(Long planTaskId) { this.planTaskId = planTaskId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getCheckEmpNo() { return checkEmpNo; }
    public void setCheckEmpNo(String checkEmpNo) { this.checkEmpNo = checkEmpNo; }
    public String getCheckEmpName() { return checkEmpName; }
    public void setCheckEmpName(String checkEmpName) { this.checkEmpName = checkEmpName; }
    public String getRecordStatus() { return recordStatus; }
    public void setRecordStatus(String recordStatus) { this.recordStatus = recordStatus; }
    public String getJudgeStatus() { return judgeStatus; }
    public void setJudgeStatus(String judgeStatus) { this.judgeStatus = judgeStatus; }
    public Integer getJudgeAttempt() { return judgeAttempt; }
    public void setJudgeAttempt(Integer judgeAttempt) { this.judgeAttempt = judgeAttempt; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
