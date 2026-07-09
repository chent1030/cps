package com.company.cps.dto;

public class CpsAreaPersonConfigRequest {
    private Long id;
    private String factory;
    private String area;
    private String line;
    private String process;
    private String empNo;
    private String empName;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public String getProcess() { return process; }
    public void setProcess(String process) { this.process = process; }
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
