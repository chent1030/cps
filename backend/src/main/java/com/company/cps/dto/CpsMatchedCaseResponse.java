package com.company.cps.dto;

import java.math.BigDecimal;

public class CpsMatchedCaseResponse {
    private Long imageId;
    private Long caseId;
    private BigDecimal confidence;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String categoryL1Name;
    private String categoryL2Name;
    private String reasonSuggestion;
    private String measureSuggestion;

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
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
}
