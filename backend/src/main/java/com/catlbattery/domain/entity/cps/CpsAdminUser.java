package com.catlbattery.domain.entity.cps;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.catlbattery.common.util.CpsStrings;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class CpsAdminUser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long id;
    private String empNo;
    private String empName;
    /** SHA-256(plainPassword + ":" + salt) hex; 登录验证用 */
    private String passwordHash;
    /** 每用户独立 salt（首次写入时随机生成 16 字节十六进制） */
    private String salt;
    /** 1=必须修改密码（首次登录/管理员重置后） */
    private Boolean mustChangePassword;
    /** ACTIVE / LOCKED */
    private String status;
    /** JSON 字符串（DB JSON 列）: 允许访问的页面 key 数组；["*"]=超管 */
    private String pagePermissions;
    private String remark;
    private LocalDateTime lastLoginAt;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPagePermissions() { return pagePermissions; }
    public void setPagePermissions(String pagePermissions) { this.pagePermissions = pagePermissions; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** 是否超管（["*"]）。JSON 解析失败时按超管兜底（保守）。 */
    @JsonIgnore
    public boolean isSuperAdmin() {
        List<String> list = parsePagePermissions();
        if (list == null) return true;
        return list.contains("*");
    }

    /** 解析 page_permissions JSON 字符串为列表。 */
    @JsonIgnore
    public List<String> parsePagePermissions() {
        if (pagePermissions == null || CpsStrings.isBlank(pagePermissions)) {
            return null;
        }
        try {
            return MAPPER.readValue(pagePermissions, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void writePagePermissions(List<String> permissions) {
        if (permissions == null) {
            this.pagePermissions = null;
            return;
        }
        try {
            this.pagePermissions = MAPPER.writeValueAsString(permissions);
        } catch (Exception e) {
            this.pagePermissions = "[]";
        }
    }
}
