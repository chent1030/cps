package com.company.cps.dto;

public class CpsInventoryItemRequest {
    private String itemCode;
    private String itemName;
    private String unit;
    private Integer stockQty;
    private Integer alertThreshold;
    private String baseCode;
    private String factory;
    private String storageRoom;
    private String roomKeeperEmpNo;
    private String roomKeeperEmpName;
    private String remark;
    private Boolean enabled;

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }
    public Integer getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(Integer alertThreshold) { this.alertThreshold = alertThreshold; }
    public String getBaseCode() { return baseCode; }
    public void setBaseCode(String baseCode) { this.baseCode = baseCode; }
    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }
    public String getStorageRoom() { return storageRoom; }
    public void setStorageRoom(String storageRoom) { this.storageRoom = storageRoom; }
    public String getRoomKeeperEmpNo() { return roomKeeperEmpNo; }
    public void setRoomKeeperEmpNo(String roomKeeperEmpNo) { this.roomKeeperEmpNo = roomKeeperEmpNo; }
    public String getRoomKeeperEmpName() { return roomKeeperEmpName; }
    public void setRoomKeeperEmpName(String roomKeeperEmpName) { this.roomKeeperEmpName = roomKeeperEmpName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
