package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * 巡检类型（Phase C1 统一模型）。
 * zoneFlag=1：按区域执行，点检项挂在 cps_inspection_area 上；
 * zoneFlag=0：点检项直接挂在类型上（checkItemIds）。
 * formSchemaJson 已废弃（旧动态表单模型，仅保留列兼容历史 extra_data）。
 */
public class CpsInspectionType {

    private Long id;
    /** 业务唯一编码，如 QUALITY/SAFETY/ROOM_CHECK */
    private String code;
    /** 显示名（巡检名称） */
    private String name;
    /** 巡检内容：这个巡检做什么、怎么做（Phase C1） */
    private String content;
    /** 是否分区域：1=按区域执行，0=点检项直挂类型（Phase C1） */
    private Integer zoneFlag;
    /** 内置类型：1=系统固定（CPS巡检），不可删除/不可停用/不可改编码（Phase D2） */
    private Integer builtin;
    /** 不分区域时直挂的点检项编码（逗号分隔 item_code）（Phase C1） */
    private String checkItemIds;
    /** 类型描述 */
    private String description;
    /** 展示图标（可选，自由约定） */
    private String icon;
    /** 主色调（可选，自由约定） */
    private String color;
    /** 是否启用：1=启用，0=停用 */
    private Integer enabled;
    /** 排序（升序） */
    private Integer sortOrder;
    /** 动态表单 schema JSON 字符串：{"fields":[{key,label,type,required,...}]} */
    private String formSchemaJson;
    /** 默认派单人（反馈人未指定时兜底） */
    private String defaultAssigneeEmpNo;
    /** 备注 */
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
