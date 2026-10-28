package com.catlbattery.domain.entity.cps.dto;

/** 字典项新建/更新请求（builtin 由服务端控制，不接受前端指定）。 */
public class CpsDictRequest {
    private String dictType;
    private String itemValue;
    private String itemLabel;
    private Integer sortNo;
    private Boolean enabled;
    private String remark;

    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }

    public String getItemValue() { return itemValue; }
    public void setItemValue(String itemValue) { this.itemValue = itemValue; }

    public String getItemLabel() { return itemLabel; }
    public void setItemLabel(String itemLabel) { this.itemLabel = itemLabel; }

    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
