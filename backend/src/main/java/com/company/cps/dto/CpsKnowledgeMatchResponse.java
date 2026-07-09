package com.company.cps.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CpsKnowledgeMatchResponse {
    private Long sourceAttachmentId;
    private Long matchedCaseId;
    private Long matchedImageId;
    private BigDecimal confidence;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String categoryL1Name;
    private String categoryL2Name;
    private String reasonSuggestion;
    private String measureSuggestion;
    private String modelName;
    private String modelVersion;
    private List<CpsMatchedCaseResponse> matchedCases = new ArrayList<>();

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

    public Long getMatchedImageId() {
        return matchedImageId;
    }

    public void setMatchedImageId(Long matchedImageId) {
        this.matchedImageId = matchedImageId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Long getCategoryL1Id() {
        return categoryL1Id;
    }

    public void setCategoryL1Id(Long categoryL1Id) {
        this.categoryL1Id = categoryL1Id;
    }

    public Long getCategoryL2Id() {
        return categoryL2Id;
    }

    public void setCategoryL2Id(Long categoryL2Id) {
        this.categoryL2Id = categoryL2Id;
    }

    public String getCategoryL1Name() {
        return categoryL1Name;
    }

    public void setCategoryL1Name(String categoryL1Name) {
        this.categoryL1Name = categoryL1Name;
    }

    public String getCategoryL2Name() {
        return categoryL2Name;
    }

    public void setCategoryL2Name(String categoryL2Name) {
        this.categoryL2Name = categoryL2Name;
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

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public List<CpsMatchedCaseResponse> getMatchedCases() {
        return matchedCases;
    }

    public void setMatchedCases(List<CpsMatchedCaseResponse> matchedCases) {
        this.matchedCases = matchedCases;
    }
}
