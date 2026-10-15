package com.company.cps.dto;

/** A3 审核员接管请求：接管原因必填（AC-27）。 */
public class CpsInitialReviewTakeOverRequest {
    private String empNo;
    private String reason;

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
