package com.company.cps.dto;

public class CpsAdminLoginResponse {
    private final String empNo;
    private final String empName;

    public CpsAdminLoginResponse(String empNo, String empName) {
        this.empNo = empNo;
        this.empName = empName;
    }

    public String getEmpNo() { return empNo; }
    public String getEmpName() { return empName; }
}
