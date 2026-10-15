package com.company.cps.dto;

import java.util.List;

/** B4 周排名响应（PRD 24.3 + D-08）。 */
public class CpsRoomCheckRankingResponse {
    private String weekStart;
    private String weekEnd;
    private List<Row> rows;

    public static class Row {
        private int rank;
        private Long roomId;
        private String roomCode;
        private String roomName;
        private int checkCount;
        private long totalScore;
        private String firstSubmittedAt;

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
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
        public String getFirstSubmittedAt() { return firstSubmittedAt; }
        public void setFirstSubmittedAt(String firstSubmittedAt) { this.firstSubmittedAt = firstSubmittedAt; }
    }

    public String getWeekStart() { return weekStart; }
    public void setWeekStart(String weekStart) { this.weekStart = weekStart; }
    public String getWeekEnd() { return weekEnd; }
    public void setWeekEnd(String weekEnd) { this.weekEnd = weekEnd; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }
}
