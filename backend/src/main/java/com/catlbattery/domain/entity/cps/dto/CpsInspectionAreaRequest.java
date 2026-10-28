package com.catlbattery.domain.entity.cps.dto;

/**
 * 巡检区域创建/更新请求（Phase C1）。
 * 字段：编码 / 所属类型 / 名称 / 工厂 / 区域 / 拉线|房型 / 工序 / 风险等级 / 责任人 / 点检项(逗号分隔)。
 */
public class CpsInspectionAreaRequest {

    private String areaCode;
    private String typeCode;
    private String areaName;
    private String factory;
    private String area;
    /** 拉线|房型：产线巡检填拉线，辅房类填房型 */
    private String lineOrRoomType;
    private String process;
    private String building;
    private String doorNo;
    /** HIGH/MEDIUM/LOW */
    private String riskLevel;
    private String keeperEmpNo;
    private String keeperEmpName;
    /** 点检项编码（逗号分隔 item_code） */
    private String checkItemIds;
    private String remark;
    private Boolean enabled;
    /** 操作人（审计/更新人字段） */
    private String operatorEmpNo;

    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getLineOrRoomType() { return lineOrRoomType; }
    public void setLineOrRoomType(String lineOrRoomType) { this.lineOrRoomType = lineOrRoomType; }
    public String getProcess() { return process; }
    public void setProcess(String process) { this.process = process; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public String getDoorNo() { return doorNo; }
    public void setDoorNo(String doorNo) { this.doorNo = doorNo; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getKeeperEmpNo() { return keeperEmpNo; }
    public void setKeeperEmpNo(String keeperEmpNo) { this.keeperEmpNo = keeperEmpNo; }
    public String getKeeperEmpName() { return keeperEmpName; }
    public void setKeeperEmpName(String keeperEmpName) { this.keeperEmpName = keeperEmpName; }
    public String getCheckItemIds() { return checkItemIds; }
    public void setCheckItemIds(String checkItemIds) { this.checkItemIds = checkItemIds; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
}
