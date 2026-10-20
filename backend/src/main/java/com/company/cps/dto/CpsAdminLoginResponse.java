package com.company.cps.dto;

import java.util.List;

public class CpsAdminLoginResponse {
    private String empNo;
    private String empName;
    private Boolean superAdmin;
    private Boolean mustChangePassword;
    private List<String> pagePermissions;

    public CpsAdminLoginResponse() { }

    public CpsAdminLoginResponse(String empNo, String empName, Boolean superAdmin, Boolean mustChangePassword, List<String> pagePermissions) {
        this.empNo = empNo;
        this.empName = empName;
        this.superAdmin = superAdmin;
        this.mustChangePassword = mustChangePassword;
        this.pagePermissions = pagePermissions;
    }

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public Boolean getSuperAdmin() { return superAdmin; }
    public void setSuperAdmin(Boolean superAdmin) { this.superAdmin = superAdmin; }
    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public List<String> getPagePermissions() { return pagePermissions; }
    public void setPagePermissions(List<String> pagePermissions) { this.pagePermissions = pagePermissions; }
}
