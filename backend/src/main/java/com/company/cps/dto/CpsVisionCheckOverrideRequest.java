package com.company.cps.dto;

/**
 * B6 视觉点检人工改判请求（POST /api/cps/room-checks/{id}/override）：
 * decision=PASS|FAIL；reason 必填；operatorEmpNo 必填（admin 弱鉴权沿用波次 2）。
 */
public class CpsVisionCheckOverrideRequest {
    private String decision;
    private String reason;
    private String operatorEmpNo;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
}