package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * 巡检区域（Phase C1 统一模型）。
 * 泛化自辅房 cps_room：拉线巡检填 lineOrRoomType=拉线，辅房类填 lineOrRoomType=房型。
 * 区域隶属某个巡检类型（typeCode），并挂载点检项（checkItemIds 逗号分隔）。
 */
public class CpsInspectionArea {

    private Long id;
    /** 区域唯一编码 */
    private String areaCode;
    /** 所属巡检类型编码（cps_inspection_type.code） */
    private String typeCode;
    /** 区域名称（房间名/拉线段名） */
    private String areaName;
    /** 工厂 */
    private String factory;
    /** 区域（厂区内分区） */
    private String area;
    /** 拉线|房型：产线巡检填拉线，辅房类填房型 */
    private String lineOrRoomType;
    /** 工序（拉线场景；辅房可空） */
    private String process;
    /** 楼栋（辅房场景沿用） */
    private String building;
    /** 门牌号（辅房场景沿用） */
    private String doorNo;
    /** 风险等级：HIGH/MEDIUM/LOW */
    private String riskLevel;
    /** 责任人工号 */
    private String keeperEmpNo;
    /** 责任人姓名 */
    private String keeperEmpName;
    /** 本区域点检项编码（逗号分隔）；空=按点检项适用范围自动匹配 */
    private String checkItemIds;
    private String remark;
    private Boolean enabled;
    /** 迁移来源 cps_room.id（追溯用） */
    private Long sourceRoomId;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Long getSourceRoomId() { return sourceRoomId; }
    public void setSourceRoomId(Long sourceRoomId) { this.sourceRoomId = sourceRoomId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
