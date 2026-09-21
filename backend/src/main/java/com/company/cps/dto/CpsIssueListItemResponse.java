package com.company.cps.dto;

import com.company.cps.domain.CpsIssueStatus;

import java.time.LocalDateTime;

public class CpsIssueListItemResponse {
    private Long id;
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
    private String aiCategoryL1Name;
    private String aiCategoryL2Name;
    private String categoryL1Name;
    private String categoryL2Name;
    private String creatorEmpNo;
    private String creatorEmpName;
    private String feedbackEmpNo;
    private String feedbackEmpName;
    private String allFlowHandlers;
    private String issueImageIds;
    private String proofImageIds;
    private String reasonAnalysis;
    private String correctiveMeasure;
    private String rectifyRemark;
    private String reviewOpinion;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getAiCategoryL1Name() { return aiCategoryL1Name; }
    public void setAiCategoryL1Name(String v) { this.aiCategoryL1Name = v; }
    public String getAiCategoryL2Name() { return aiCategoryL2Name; }
    public void setAiCategoryL2Name(String v) { this.aiCategoryL2Name = v; }
    public String getCategoryL1Name() { return categoryL1Name; }
    public void setCategoryL1Name(String v) { this.categoryL1Name = v; }
    public String getCategoryL2Name() { return categoryL2Name; }
    public void setCategoryL2Name(String v) { this.categoryL2Name = v; }
    public String getCreatorEmpNo() { return creatorEmpNo; }
    public void setCreatorEmpNo(String v) { this.creatorEmpNo = v; }
    public String getCreatorEmpName() { return creatorEmpName; }
    public void setCreatorEmpName(String v) { this.creatorEmpName = v; }
    public String getFeedbackEmpNo() { return feedbackEmpNo; }
    public void setFeedbackEmpNo(String v) { this.feedbackEmpNo = v; }
    public String getFeedbackEmpName() { return feedbackEmpName; }
    public void setFeedbackEmpName(String v) { this.feedbackEmpName = v; }
    public String getAllFlowHandlers() { return allFlowHandlers; }
    public void setAllFlowHandlers(String v) { this.allFlowHandlers = v; }
    public String getIssueImageIds() { return issueImageIds; }
    public void setIssueImageIds(String v) { this.issueImageIds = v; }
    public String getProofImageIds() { return proofImageIds; }
    public void setProofImageIds(String v) { this.proofImageIds = v; }
    public String getReasonAnalysis() { return reasonAnalysis; }
    public void setReasonAnalysis(String v) { this.reasonAnalysis = v; }
    public String getCorrectiveMeasure() { return correctiveMeasure; }
    public void setCorrectiveMeasure(String v) { this.correctiveMeasure = v; }
    public String getRectifyRemark() { return rectifyRemark; }
    public void setRectifyRemark(String v) { this.rectifyRemark = v; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String v) { this.reviewOpinion = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
