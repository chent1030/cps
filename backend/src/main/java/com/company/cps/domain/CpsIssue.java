package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsIssue {
    private Long id;
    private String issueNo;
    private CpsIssueStatus status;
    private String factory;
    private String area;
    private String line;
    private String process;
    private Long aiCategoryL1Id;
    private Long aiCategoryL2Id;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private Boolean categoryModifiedFlag;
    private String description;
    private String creatorEmpNo;
    private String feedbackEmpNo;
    private String responsibleEmpNo;
    private String proofEmpNo;
    private String reviewerEmpNo;
    private String currentHandlerEmpNo;
    private String creatorEmpName;
    private String feedbackEmpName;
    private String responsibleEmpName;
    private String proofEmpName;
    private String reviewerEmpName;
    private String currentHandlerEmpName;
    private String reasonAnalysis;
    private String correctiveMeasure;
    private String rectifyRemark;
    private String reviewOpinion;
    private LocalDateTime submitTime;
    private LocalDateTime closeTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
    public Long getAiCategoryL1Id() { return aiCategoryL1Id; }
    public void setAiCategoryL1Id(Long aiCategoryL1Id) { this.aiCategoryL1Id = aiCategoryL1Id; }
    public Long getAiCategoryL2Id() { return aiCategoryL2Id; }
    public void setAiCategoryL2Id(Long aiCategoryL2Id) { this.aiCategoryL2Id = aiCategoryL2Id; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public Boolean getCategoryModifiedFlag() { return categoryModifiedFlag; }
    public void setCategoryModifiedFlag(Boolean categoryModifiedFlag) { this.categoryModifiedFlag = categoryModifiedFlag; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatorEmpNo() { return creatorEmpNo; }
    public void setCreatorEmpNo(String creatorEmpNo) { this.creatorEmpNo = creatorEmpNo; }
    public String getFeedbackEmpNo() { return feedbackEmpNo; }
    public void setFeedbackEmpNo(String feedbackEmpNo) { this.feedbackEmpNo = feedbackEmpNo; }
    public String getResponsibleEmpNo() { return responsibleEmpNo; }
    public void setResponsibleEmpNo(String responsibleEmpNo) { this.responsibleEmpNo = responsibleEmpNo; }
    public String getProofEmpNo() { return proofEmpNo; }
    public void setProofEmpNo(String proofEmpNo) { this.proofEmpNo = proofEmpNo; }
    public String getReviewerEmpNo() { return reviewerEmpNo; }
    public void setReviewerEmpNo(String reviewerEmpNo) { this.reviewerEmpNo = reviewerEmpNo; }
    public String getCurrentHandlerEmpNo() { return currentHandlerEmpNo; }
    public void setCurrentHandlerEmpNo(String currentHandlerEmpNo) { this.currentHandlerEmpNo = currentHandlerEmpNo; }
    public String getCreatorEmpName() { return creatorEmpName; }
    public void setCreatorEmpName(String creatorEmpName) { this.creatorEmpName = creatorEmpName; }
    public String getFeedbackEmpName() { return feedbackEmpName; }
    public void setFeedbackEmpName(String feedbackEmpName) { this.feedbackEmpName = feedbackEmpName; }
    public String getResponsibleEmpName() { return responsibleEmpName; }
    public void setResponsibleEmpName(String responsibleEmpName) { this.responsibleEmpName = responsibleEmpName; }
    public String getProofEmpName() { return proofEmpName; }
    public void setProofEmpName(String proofEmpName) { this.proofEmpName = proofEmpName; }
    public String getReviewerEmpName() { return reviewerEmpName; }
    public void setReviewerEmpName(String reviewerEmpName) { this.reviewerEmpName = reviewerEmpName; }
    public String getCurrentHandlerEmpName() { return currentHandlerEmpName; }
    public void setCurrentHandlerEmpName(String currentHandlerEmpName) { this.currentHandlerEmpName = currentHandlerEmpName; }
    public String getReasonAnalysis() { return reasonAnalysis; }
    public void setReasonAnalysis(String reasonAnalysis) { this.reasonAnalysis = reasonAnalysis; }
    public String getCorrectiveMeasure() { return correctiveMeasure; }
    public void setCorrectiveMeasure(String correctiveMeasure) { this.correctiveMeasure = correctiveMeasure; }
    public String getRectifyRemark() { return rectifyRemark; }
    public void setRectifyRemark(String rectifyRemark) { this.rectifyRemark = rectifyRemark; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String reviewOpinion) { this.reviewOpinion = reviewOpinion; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
