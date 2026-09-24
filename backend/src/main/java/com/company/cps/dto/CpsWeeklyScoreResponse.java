package com.company.cps.dto;

import java.time.LocalDate;
import java.util.List;

/** B4 周评分列表条目响应。 */
public class CpsWeeklyScoreResponse {

    private Long id;
    private LocalDate weekStartDate;
    private String empNo;
    private String empName;
    private Long regionSupervisorId;
    private String regionSupervisorName;
    private Integer totalScore;
    private Integer rank;
    private Integer roomCheckCount;
    private Integer photoCount;
    private Boolean naturalWeekFlag;
    private List<CpsWeeklyScoreLineResponse> lines;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public Long getRegionSupervisorId() { return regionSupervisorId; }
    public void setRegionSupervisorId(Long regionSupervisorId) {
        this.regionSupervisorId = regionSupervisorId;
    }
    public String getRegionSupervisorName() { return regionSupervisorName; }
    public void setRegionSupervisorName(String regionSupervisorName) {
        this.regionSupervisorName = regionSupervisorName;
    }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public Integer getRoomCheckCount() { return roomCheckCount; }
    public void setRoomCheckCount(Integer roomCheckCount) { this.roomCheckCount = roomCheckCount; }
    public Integer getPhotoCount() { return photoCount; }
    public void setPhotoCount(Integer photoCount) { this.photoCount = photoCount; }
    public Boolean getNaturalWeekFlag() { return naturalWeekFlag; }
    public void setNaturalWeekFlag(Boolean naturalWeekFlag) { this.naturalWeekFlag = naturalWeekFlag; }
    public List<CpsWeeklyScoreLineResponse> getLines() { return lines; }
    public void setLines(List<CpsWeeklyScoreLineResponse> lines) { this.lines = lines; }
}
