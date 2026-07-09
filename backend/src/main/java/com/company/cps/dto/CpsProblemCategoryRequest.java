package com.company.cps.dto;

public class CpsProblemCategoryRequest {
    private Long id;
    private Long parentId;
    private Integer categoryLevel;
    private String categoryName;
    private String categoryCode;
    private Integer sortNo;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getCategoryLevel() { return categoryLevel; }
    public void setCategoryLevel(Integer categoryLevel) { this.categoryLevel = categoryLevel; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
