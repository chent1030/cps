package com.company.cps.dto;

public class CpsInspectionPlanApproveRequest {
    private Integer lockVersion;
    private String approver;
    private String approverName;
    private String comment;

    public Integer getLockVersion() { return lockVersion; }
    public void setLockVersion(Integer lockVersion) { this.lockVersion = lockVersion; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
