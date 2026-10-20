package com.company.cps.dto;

import java.util.List;

public class CpsAdminUserCreateRequest {
    private String empNo;
    private String empName;
    /** 新账号的初始密码；可空，空则后端随机生成并通过 reset 接口下发 */
    private String initialPassword;
    private List<String> pagePermissions;
    private String remark;
    private String currentEmpNo;

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
    public List<String> getPagePermissions() { return pagePermissions; }
    public void setPagePermissions(List<String> pagePermissions) { this.pagePermissions = pagePermissions; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCurrentEmpNo() { return currentEmpNo; }
    public void setCurrentEmpNo(String currentEmpNo) { this.currentEmpNo = currentEmpNo; }
}
