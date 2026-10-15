package com.company.cps.dto;

/** E3 预警人工处理请求：action=IGNORE（忽略）/ CLOSE（关闭，必填原因）。 */
public class CpsInventoryAlertHandleRequest {
    private String action; // IGNORE / CLOSE
    private String reason;
    private String operatorEmpNo;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
}
