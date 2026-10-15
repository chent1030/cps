package com.company.cps.dto;

/** A4 admin 手动重触发请求：重触发原因必填（留痕）。 */
public class CpsAdminRetriggerRequest {
    private String operatorEmpNo;
    private String reason;

    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
