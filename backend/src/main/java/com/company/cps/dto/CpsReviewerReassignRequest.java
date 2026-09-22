package com.company.cps.dto;

/**
 * A2 管理员改配审核员请求（PRD §30.2，AC-25）：
 * 未完成审核单转新审核员，原审核员失权；不重置 AI 初审计时；reason 必填留痕。
 */
public class CpsReviewerReassignRequest {
    private String reviewerEmpNo;
    private String reason;
    private String operatorEmpNo;

    public String getReviewerEmpNo() { return reviewerEmpNo; }
    public void setReviewerEmpNo(String reviewerEmpNo) { this.reviewerEmpNo = reviewerEmpNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
}
