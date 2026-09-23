package com.company.cps.dto;

/** B3 开启点检请求：planTaskId 可空（临时点检），roomId 必填。 */
public class CpsRoomCheckStartRequest {
    private Long planTaskId;
    private Long roomId;
    private String empNo;

    public Long getPlanTaskId() { return planTaskId; }
    public void setPlanTaskId(Long planTaskId) { this.planTaskId = planTaskId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
}
