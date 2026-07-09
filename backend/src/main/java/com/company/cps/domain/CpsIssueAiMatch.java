package com.company.cps.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CpsIssueAiMatch {
    private Long id;
    private Long issueId;
    private Long sourceAttachmentId;
    private Long matchedCaseId;
    private BigDecimal confidence;
    private Long aiCategoryL1Id;
    private Long aiCategoryL2Id;
    private String aiCategoryL1Name;
    private String aiCategoryL2Name;
    private String reasonSuggestion;
    private String measureSuggestion;
    private String topkJson;
    private String rawRequest;
    private String rawResponse;
    private Long confirmedCategoryL1Id;
    private Long confirmedCategoryL2Id;
    private String confirmedReason;
    private String confirmedMeasure;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIssueId() {
        return issueId;
    }

    public void setIssueId(Long issueId) {
        this.issueId = issueId;
    }

    public Long getSourceAttachmentId() {
        return sourceAttachmentId;
    }

    public void setSourceAttachmentId(Long sourceAttachmentId) {
        this.sourceAttachmentId = sourceAttachmentId;
    }

    public Long getMatchedCaseId() {
        return matchedCaseId;
    }

    public void setMatchedCaseId(Long matchedCaseId) {
        this.matchedCaseId = matchedCaseId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Long getAiCategoryL1Id() {
        return aiCategoryL1Id;
    }

    public void setAiCategoryL1Id(Long aiCategoryL1Id) {
        this.aiCategoryL1Id = aiCategoryL1Id;
    }

    public Long getAiCategoryL2Id() {
        return aiCategoryL2Id;
    }

    public void setAiCategoryL2Id(Long aiCategoryL2Id) {
        this.aiCategoryL2Id = aiCategoryL2Id;
    }

    public String getAiCategoryL1Name() {
        return aiCategoryL1Name;
    }

    public void setAiCategoryL1Name(String aiCategoryL1Name) {
        this.aiCategoryL1Name = aiCategoryL1Name;
    }

    public String getAiCategoryL2Name() {
        return aiCategoryL2Name;
    }

    public void setAiCategoryL2Name(String aiCategoryL2Name) {
        this.aiCategoryL2Name = aiCategoryL2Name;
    }

    public String getReasonSuggestion() {
        return reasonSuggestion;
    }

    public void setReasonSuggestion(String reasonSuggestion) {
        this.reasonSuggestion = reasonSuggestion;
    }

    public String getMeasureSuggestion() {
        return measureSuggestion;
    }

    public void setMeasureSuggestion(String measureSuggestion) {
        this.measureSuggestion = measureSuggestion;
    }

    public String getTopkJson() {
        return topkJson;
    }

    public void setTopkJson(String topkJson) {
        this.topkJson = topkJson;
    }

    public String getRawRequest() {
        return rawRequest;
    }

    public void setRawRequest(String rawRequest) {
        this.rawRequest = rawRequest;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public Long getConfirmedCategoryL1Id() {
        return confirmedCategoryL1Id;
    }

    public void setConfirmedCategoryL1Id(Long confirmedCategoryL1Id) {
        this.confirmedCategoryL1Id = confirmedCategoryL1Id;
    }

    public Long getConfirmedCategoryL2Id() {
        return confirmedCategoryL2Id;
    }

    public void setConfirmedCategoryL2Id(Long confirmedCategoryL2Id) {
        this.confirmedCategoryL2Id = confirmedCategoryL2Id;
    }

    public String getConfirmedReason() {
        return confirmedReason;
    }

    public void setConfirmedReason(String confirmedReason) {
        this.confirmedReason = confirmedReason;
    }

    public String getConfirmedMeasure() {
        return confirmedMeasure;
    }

    public void setConfirmedMeasure(String confirmedMeasure) {
        this.confirmedMeasure = confirmedMeasure;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
