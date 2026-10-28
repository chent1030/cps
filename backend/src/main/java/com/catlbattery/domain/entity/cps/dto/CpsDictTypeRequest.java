package com.catlbattery.domain.entity.cps.dto;

/** 字典类型保存请求（/api/cps/admin/dict-types）：新建时 typeCode 必填（内置类型不可改码）。 */
public class CpsDictTypeRequest {

    private String typeCode;
    private String typeName;
    private String description;
    private Integer sortNo;
    private Boolean enabled;

    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
