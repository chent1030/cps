package com.company.cps.domain;

import java.time.LocalDateTime;

/** E2 出入库流水（cps_inventory_txn，PRD §25.2/§25.3；AC-33）。qty 带符号：IN>0 / OUT<0 / ADJUST&lt;&gt;0。 */
public class CpsInventoryTxn {
    private Long id;
    private Long itemId;
    private String txnType; // IN / OUT / ADJUST
    private Integer qty;
    private Integer beforeQty;
    private Integer afterQty;
    private String unit;
    private String operatorEmpNo;
    private String operatorName;
    private String remark;
    private LocalDateTime createdAt;
    /** 列表联查冗余（非表列） */
    private String itemCode;
    private String itemName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public Integer getBeforeQty() { return beforeQty; }
    public void setBeforeQty(Integer beforeQty) { this.beforeQty = beforeQty; }
    public Integer getAfterQty() { return afterQty; }
    public void setAfterQty(Integer afterQty) { this.afterQty = afterQty; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
}
