package com.company.cps.dto;

public class CpsRoomRequest {
    private String roomCode;
    private String building;
    private String doorNo;
    private String roomName;
    private String roomType;
    private String riskLevel;
    private String deptName;
    private String baseCode;
    private String factory;
    private String keeperEmpNo;
    private String keeperEmpName;
    private String keeperManagerEmpNo;
    private String keeperManagerEmpName;
    private Boolean enabled;

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public String getDoorNo() { return doorNo; }
    public void setDoorNo(String doorNo) { this.doorNo = doorNo; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getBaseCode() { return baseCode; }
    public void setBaseCode(String baseCode) { this.baseCode = baseCode; }
    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }
    public String getKeeperEmpNo() { return keeperEmpNo; }
    public void setKeeperEmpNo(String keeperEmpNo) { this.keeperEmpNo = keeperEmpNo; }
    public String getKeeperEmpName() { return keeperEmpName; }
    public void setKeeperEmpName(String keeperEmpName) { this.keeperEmpName = keeperEmpName; }
    public String getKeeperManagerEmpNo() { return keeperManagerEmpNo; }
    public void setKeeperManagerEmpNo(String keeperManagerEmpNo) { this.keeperManagerEmpNo = keeperManagerEmpNo; }
    public String getKeeperManagerEmpName() { return keeperManagerEmpName; }
    public void setKeeperManagerEmpName(String keeperManagerEmpName) { this.keeperManagerEmpName = keeperManagerEmpName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
