package com.company.cps.dto;

public class CpsKnowledgeCaseRequest {
    private Long id;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String categoryL1Name;
    private String categoryL2Name;
    private Boolean enabled;
    private String empNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public String getCategoryL1Name() { return categoryL1Name; }
    public void setCategoryL1Name(String categoryL1Name) { this.categoryL1Name = categoryL1Name; }
    public String getCategoryL2Name() { return categoryL2Name; }
    public void setCategoryL2Name(String categoryL2Name) { this.categoryL2Name = categoryL2Name; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
}
