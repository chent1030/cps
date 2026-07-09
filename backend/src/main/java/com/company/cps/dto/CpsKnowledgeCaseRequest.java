package com.company.cps.dto;

public class CpsKnowledgeCaseRequest {
    private Long id;
    private String caseCode;
    private String caseTitle;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String categoryL1Name;
    private String categoryL2Name;
    private String scopeRemark;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }
    public String getCaseTitle() { return caseTitle; }
    public void setCaseTitle(String caseTitle) { this.caseTitle = caseTitle; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public String getCategoryL1Name() { return categoryL1Name; }
    public void setCategoryL1Name(String categoryL1Name) { this.categoryL1Name = categoryL1Name; }
    public String getCategoryL2Name() { return categoryL2Name; }
    public void setCategoryL2Name(String categoryL2Name) { this.categoryL2Name = categoryL2Name; }
    public String getScopeRemark() { return scopeRemark; }
    public void setScopeRemark(String scopeRemark) { this.scopeRemark = scopeRemark; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
