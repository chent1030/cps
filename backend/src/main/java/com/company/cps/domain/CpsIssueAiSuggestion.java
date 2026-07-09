package com.company.cps.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CpsIssueAiSuggestion {
    private Long id;
    private Long issueId;
    private Long sourceAttachmentId;
    private Long aiCategoryL1Id;
    private String aiCategoryL1Name;
    private Long aiCategoryL2Id;
    private String aiCategoryL2Name;
    private String reasonSuggestion;
    private String measureSuggestion;
    private String rawRequest;
    private String rawResponse;
    private BigDecimal confidence;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Long getSourceAttachmentId() { return sourceAttachmentId; }
    public void setSourceAttachmentId(Long sourceAttachmentId) { this.sourceAttachmentId = sourceAttachmentId; }
    public Long getAiCategoryL1Id() { return aiCategoryL1Id; }
    public void setAiCategoryL1Id(Long aiCategoryL1Id) { this.aiCategoryL1Id = aiCategoryL1Id; }
    public String getAiCategoryL1Name() { return aiCategoryL1Name; }
    public void setAiCategoryL1Name(String aiCategoryL1Name) { this.aiCategoryL1Name = aiCategoryL1Name; }
    public Long getAiCategoryL2Id() { return aiCategoryL2Id; }
    public void setAiCategoryL2Id(Long aiCategoryL2Id) { this.aiCategoryL2Id = aiCategoryL2Id; }
    public String getAiCategoryL2Name() { return aiCategoryL2Name; }
    public void setAiCategoryL2Name(String aiCategoryL2Name) { this.aiCategoryL2Name = aiCategoryL2Name; }
    public String getReasonSuggestion() { return reasonSuggestion; }
    public void setReasonSuggestion(String reasonSuggestion) { this.reasonSuggestion = reasonSuggestion; }
    public String getMeasureSuggestion() { return measureSuggestion; }
    public void setMeasureSuggestion(String measureSuggestion) { this.measureSuggestion = measureSuggestion; }
    public String getRawRequest() { return rawRequest; }
    public void setRawRequest(String rawRequest) { this.rawRequest = rawRequest; }
    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
