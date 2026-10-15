package com.company.cps.dto;

import java.time.LocalDateTime;

/**
 * B6 视觉点检分页查询过滤（admin 端 GET /api/cps/room-checks）：
 * roomId?/checkItemId?/status?/startTime?/endTime? 任选；
 * page/size 必填（service 层 off-bound 处理）。
 */
public class CpsVisionCheckListFilter {
    private Long roomId;
    private Long checkItemId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer page;
    private Integer size;

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getCheckItemId() { return checkItemId; }
    public void setCheckItemId(Long checkItemId) { this.checkItemId = checkItemId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}