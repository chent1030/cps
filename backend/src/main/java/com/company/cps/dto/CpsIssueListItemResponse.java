package com.company.cps.dto;

import com.company.cps.domain.CpsIssueStatus;

import java.time.LocalDateTime;

public class CpsIssueListItemResponse {
    private Long id;
    private String issueNo;
    private CpsIssueStatus status;
    private String factory;
    private String area;
    private String line;
    private String process;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String description;
    private String currentHandlerEmpNo;
    private String currentHandlerEmpName;
    private LocalDateTime submitTime;
    private Boolean overdue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public CpsIssueStatus getStatus() { return status; }
    public void setStatus(CpsIssueStatus status) { this.status = status; }
    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public String getProcess() { return process; }
    public void setProcess(String process) { this.process = process; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCurrentHandlerEmpNo() { return currentHandlerEmpNo; }
    public void setCurrentHandlerEmpNo(String currentHandlerEmpNo) { this.currentHandlerEmpNo = currentHandlerEmpNo; }
    public String getCurrentHandlerEmpName() { return currentHandlerEmpName; }
    public void setCurrentHandlerEmpName(String currentHandlerEmpName) { this.currentHandlerEmpName = currentHandlerEmpName; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public Boolean getOverdue() { return overdue; }
    public void setOverdue(Boolean overdue) { this.overdue = overdue; }
}
