package com.company.cps.dto;

public class CpsAdminPasswordChangeRequest {
    private String empNo;
    private String oldPassword;
    private String newPassword;
    /** 管理员重置时由调用方传 */
    private String currentEmpNo;

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getCurrentEmpNo() { return currentEmpNo; }
    public void setCurrentEmpNo(String currentEmpNo) { this.currentEmpNo = currentEmpNo; }
}
