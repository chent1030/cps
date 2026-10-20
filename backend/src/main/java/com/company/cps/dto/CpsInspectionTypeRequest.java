package com.company.cps.dto;

/**
 * 管理端创建/更新巡检类型请求体（Phase C1 统一模型）。
 * 固定字段：编码 / 巡检名称 / 巡检内容 / 是否分区域；
 * 不分区域时点检项直接挂类型（checkItemIds 逗号分隔 item_code）。
 * formSchemaJson 已废弃，不再必填（历史兼容可传）。
 *
 * 不使用 javax.validation 注解：校验在 CpsInspectionTypeService 内统一处理。
 */
public class CpsInspectionTypeRequest {

    private String code;
    private String name;
    /** 巡检内容（这个巡检做什么、怎么做） */
    private String content;
    /** 是否分区域：1=按区域执行，0=点检项直挂类型 */
    private Integer zoneFlag = 0;
    /** 不分区域时直挂的点检项编码（逗号分隔） */
    private String checkItemIds;
    private String description;
    private String icon;
    private String color;
    private Integer enabled = 1;
    private Integer sortOrder = 0;
    /** 【已废弃】旧动态表单 schema；传入则原样保存，不再校验 */
    private String formSchemaJson;
    private String defaultAssigneeEmpNo;
    private String remark;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getZoneFlag() { return zoneFlag; }
    public void setZoneFlag(Integer zoneFlag) { this.zoneFlag = zoneFlag; }

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
}
