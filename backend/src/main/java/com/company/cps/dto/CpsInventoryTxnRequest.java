package com.company.cps.dto;

/** E2 出入库登记请求（AC-33）。qty 带符号语义：IN>0 / OUT&lt;0 / ADJUST&lt;&gt;0，由服务层校验。 */
public class CpsInventoryTxnRequest {
    private Long itemId;
    private String txnType; // IN / OUT / ADJUST
    private Integer qty;
    private String operatorEmpNo;
    private String operatorName;
    private String remark;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
