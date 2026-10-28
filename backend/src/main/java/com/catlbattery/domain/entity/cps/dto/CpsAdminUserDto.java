package com.catlbattery.domain.entity.cps.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CpsAdminUserDto {
    private Long id;
    private String empNo;
    private String empName;
    private String status;
    private Boolean mustChangePassword;
    private Boolean superAdmin;
    private List<String> pagePermissions;
    private String remark;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public Boolean getSuperAdmin() { return superAdmin; }
    public void setSuperAdmin(Boolean superAdmin) { this.superAdmin = superAdmin; }
    public List<String> getPagePermissions() { return pagePermissions; }
    public void setPagePermissions(List<String> pagePermissions) { this.pagePermissions = pagePermissions; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
