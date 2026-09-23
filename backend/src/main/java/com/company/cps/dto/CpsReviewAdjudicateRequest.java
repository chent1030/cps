package com.company.cps.dto;

/** A3 人工裁决请求：decision=APPROVE（通过关闭）/REJECT（退回整改），裁决理由必填。 */
public class CpsReviewAdjudicateRequest {
    private String empNo;
    private String decision;
    private String reason;

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
