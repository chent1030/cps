package com.company.cps.dto;

import java.util.List;

/** B4 房间周评分明细：逐次检查时间+单次得分+不合格扣分明细（PRD 24.3 报告口径）。 */
public class CpsRoomCheckRoomWeekDetailResponse {
    private String weekStart;
    private String weekEnd;
    private Long roomId;
    private String roomCode;
    private String roomName;
    private int checkCount;
    private long totalScore;
    private int rank;
    private List<Record> records;

    public static class Record {
        private Long recordId;
        private String submittedAt;
        private Integer score;
        private String checkEmpNo;
        private List<Deduct> deducts;

        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public String getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public String getCheckEmpNo() { return checkEmpNo; }
        public void setCheckEmpNo(String checkEmpNo) { this.checkEmpNo = checkEmpNo; }
        public List<Deduct> getDeducts() { return deducts; }
        public void setDeducts(List<Deduct> deducts) { this.deducts = deducts; }
    }

    public static class Deduct {
        private Long itemId;
        private String itemCode;
        private String content;
        private Integer deductScore;
        private String judgeReason;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public String getItemCode() { return itemCode; }
        public void setItemCode(String itemCode) { this.itemCode = itemCode; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getDeductScore() { return deductScore; }
        public void setDeductScore(Integer deductScore) { this.deductScore = deductScore; }
        public String getJudgeReason() { return judgeReason; }
        public void setJudgeReason(String judgeReason) { this.judgeReason = judgeReason; }
    }

    public String getWeekStart() { return weekStart; }
    public void setWeekStart(String weekStart) { this.weekStart = weekStart; }
    public String getWeekEnd() { return weekEnd; }
    public void setWeekEnd(String weekEnd) { this.weekEnd = weekEnd; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public int getCheckCount() { return checkCount; }
    public void setCheckCount(int checkCount) { this.checkCount = checkCount; }
    public long getTotalScore() { return totalScore; }
    public void setTotalScore(long totalScore) { this.totalScore = totalScore; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public List<Record> getRecords() { return records; }
    public void setRecords(List<Record> records) { this.records = records; }
}
