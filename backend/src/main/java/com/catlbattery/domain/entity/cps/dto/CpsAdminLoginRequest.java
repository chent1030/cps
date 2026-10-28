package com.catlbattery.domain.entity.cps.dto;

public class CpsAdminLoginRequest {
    private String empNo;
    private String password;

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
