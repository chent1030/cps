package com.company.cps.domain;

import java.time.LocalDate;

/**
 * B4 周评分汇总（cps_weekly_score）：
 * - 一员工（emp_no）一周（week_start_date，自然周周一）一条汇总；
 * - region_supervisor_id 冗余存储以便按区域督导分组排名；
 * - total_score = base(100) + 各项 score_delta 之和；
 * - natural_week_flag=true 即自然周（周一00:00 Asia/Shanghai 界定）；
 *   false 时通常用于人工补录/补排（如人工 recompute），保留字段兼容历史数据。
 */
public class CpsWeeklyScore {

    private Long id;
    private LocalDate weekStartDate;
    private String empNo;
    private String empName;
    private Long regionSupervisorId;
    private String regionSupervisorName;
    private Integer totalScore;
    private Integer roomCheckCount;
    private Integer photoCount;
    private Boolean naturalWeekFlag;
    private String updatedBy;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public Long getRegionSupervisorId() { return regionSupervisorId; }
    public void setRegionSupervisorId(Long regionSupervisorId) { this.regionSupervisorId = regionSupervisorId; }
    public String getRegionSupervisorName() { return regionSupervisorName; }
    public void setRegionSupervisorName(String regionSupervisorName) {
        this.regionSupervisorName = regionSupervisorName;
    }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getRoomCheckCount() { return roomCheckCount; }
    public void setRoomCheckCount(Integer roomCheckCount) { this.roomCheckCount = roomCheckCount; }
    public Integer getPhotoCount() { return photoCount; }
    public void setPhotoCount(Integer photoCount) { this.photoCount = photoCount; }
    public Boolean getNaturalWeekFlag() { return naturalWeekFlag; }
    public void setNaturalWeekFlag(Boolean naturalWeekFlag) { this.naturalWeekFlag = naturalWeekFlag; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
