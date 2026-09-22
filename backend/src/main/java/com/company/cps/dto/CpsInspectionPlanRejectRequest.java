package com.company.cps.dto;

public class CpsInspectionPlanRejectRequest {
    private Integer lockVersion;
    private String approver;
    private String approverName;
    /** 必填；与 §22.2 控制要求一致。 */
    private String reason;

    public Integer getLockVersion() { return lockVersion; }
    public void setLockVersion(Integer lockVersion) { this.lockVersion = lockVersion; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
