package com.catlbattery.domain.entity.cps.dto;

import java.util.List;

public class CpsAdminUserUpdateRequest {
    private String empName;
    private String status;
    private List<String> pagePermissions;
    private String remark;
    private String currentEmpNo;

    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getPagePermissions() { return pagePermissions; }
    public void setPagePermissions(List<String> pagePermissions) { this.pagePermissions = pagePermissions; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCurrentEmpNo() { return currentEmpNo; }
    public void setCurrentEmpNo(String currentEmpNo) { this.currentEmpNo = currentEmpNo; }
}
