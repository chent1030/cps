package com.catlbattery.domain.entity.cps.dto;

/** B：admin 管理台点检记录列表行（PRD §22 追溯：明细数/不合格数/人工修正数）。 */
public class CpsRoomCheckAdminRowDto {
    private Long id;
    private String roomCode;
    private String roomName;
    private String checkEmpNo;
    private String checkEmpName;
    private String recordStatus;
    private String judgeStatus;
    private Integer judgeAttempt;
    private Integer score;
    private String submittedAt;
    private Integer itemCount;
    private Integer failCount;
    private Integer modifiedCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
    public Integer getModifiedCount() { return modifiedCount; }
    public void setModifiedCount(Integer modifiedCount) { this.modifiedCount = modifiedCount; }
}
