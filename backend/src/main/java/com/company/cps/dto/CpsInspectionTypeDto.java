package com.company.cps.dto;

import com.company.cps.domain.CpsInspectionType;

import java.time.LocalDateTime;

/**
 * 巡检类型 DTO（管理端与 Android 端共用）。Android 端只读 enabled=1 的记录，
 * 但完整 schema 仍返回，便于后期客户端在草稿状态下做预校验。
 */
public class CpsInspectionTypeDto {

    private Long id;
    private String code;
    private String name;
    /** 巡检内容（Phase C1） */
    private String content;
    /** 是否分区域：1=按区域执行，0=点检项直挂类型（Phase C1） */
    private Integer zoneFlag;
    /** 内置类型：1=系统固定（CPS巡检），不可删除/停用/改编码（Phase D2） */
    private Integer builtin;
    /** 不分区域时直挂的点检项编码（逗号分隔）（Phase C1） */
    private String checkItemIds;
    private String description;
    private String icon;
    private String color;
    private Integer enabled;
    private Integer sortOrder;
    /** 【已废弃 Phase C1】旧动态表单 schema，仅历史兼容返回 */
    private String formSchemaJson;
    private String defaultAssigneeEmpNo;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CpsInspectionTypeDto from(CpsInspectionType t) {
        if (t == null) return null;
        CpsInspectionTypeDto dto = new CpsInspectionTypeDto();
        dto.id = t.getId();
        dto.code = t.getCode();
        dto.name = t.getName();
        dto.content = t.getContent();
        dto.zoneFlag = t.getZoneFlag();
        dto.builtin = t.getBuiltin();
        dto.checkItemIds = t.getCheckItemIds();
        dto.description = t.getDescription();
        dto.icon = t.getIcon();
        dto.color = t.getColor();
        dto.enabled = t.getEnabled();
        dto.sortOrder = t.getSortOrder();
        dto.formSchemaJson = t.getFormSchemaJson();
        dto.defaultAssigneeEmpNo = t.getDefaultAssigneeEmpNo();
        dto.remark = t.getRemark();
        dto.createdAt = t.getCreatedAt();
        dto.updatedAt = t.getUpdatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getZoneFlag() { return zoneFlag; }
    public void setZoneFlag(Integer zoneFlag) { this.zoneFlag = zoneFlag; }

    public Integer getBuiltin() { return builtin; }
    public void setBuiltin(Integer builtin) { this.builtin = builtin; }

    public String getCheckItemIds() { return checkItemIds; }
    public void setCheckItemIds(String checkItemIds) { this.checkItemIds = checkItemIds; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getFormSchemaJson() { return formSchemaJson; }
    public void setFormSchemaJson(String formSchemaJson) { this.formSchemaJson = formSchemaJson; }

    public String getDefaultAssigneeEmpNo() { return defaultAssigneeEmpNo; }
    public void setDefaultAssigneeEmpNo(String defaultAssigneeEmpNo) { this.defaultAssigneeEmpNo = defaultAssigneeEmpNo; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
