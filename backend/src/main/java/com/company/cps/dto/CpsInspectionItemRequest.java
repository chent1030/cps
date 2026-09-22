package com.company.cps.dto;

public class CpsInspectionItemRequest {
    private String itemCode;
    private String itemName;
    private String factory;
    private Boolean enabled;
    private String remark;

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
