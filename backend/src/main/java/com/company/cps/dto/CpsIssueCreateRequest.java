package com.company.cps.dto;

import java.util.List;

public class CpsIssueCreateRequest {
    private String factory;
    private String area;
    private String line;
    private String process;
    private Long aiCategoryL1Id;
    private Long aiCategoryL2Id;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String description;
    private String feedbackEmpNo;
    private List<Long> issueAttachmentIds;
    private CpsIssueAiSuggestionRequest aiSuggestion;

    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public String getProcess() { return process; }
    public void setProcess(String process) { this.process = process; }
    public Long getAiCategoryL1Id() { return aiCategoryL1Id; }
    public void setAiCategoryL1Id(Long aiCategoryL1Id) { this.aiCategoryL1Id = aiCategoryL1Id; }
    public Long getAiCategoryL2Id() { return aiCategoryL2Id; }
    public void setAiCategoryL2Id(Long aiCategoryL2Id) { this.aiCategoryL2Id = aiCategoryL2Id; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFeedbackEmpNo() { return feedbackEmpNo; }
    public void setFeedbackEmpNo(String feedbackEmpNo) { this.feedbackEmpNo = feedbackEmpNo; }
    public List<Long> getIssueAttachmentIds() { return issueAttachmentIds; }
    public void setIssueAttachmentIds(List<Long> issueAttachmentIds) { this.issueAttachmentIds = issueAttachmentIds; }
    public CpsIssueAiSuggestionRequest getAiSuggestion() { return aiSuggestion; }
    public void setAiSuggestion(CpsIssueAiSuggestionRequest aiSuggestion) { this.aiSuggestion = aiSuggestion; }
}
