package com.company.cps.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 初审逐项意见（cps_initial_review_item）：L/P 实际值落库供核对；缺失记 SKIPPED 不伪造通过。 */
public class CpsInitialReviewItem {
    private Long id;
    private Long reviewId;
    private Long taskId;
    /** IMAGE_COMPARE/MEASURE_SIMILARITY/TEXT_VALIDITY/TEXT_LENGTH/PUNCTUATION_RATIO。 */
    private String checkType;
    /** reason/short_term/long_term。 */
    private String fieldName;
    /** PASS/FAIL/WARN/SKIPPED。 */
    private String verdict;
    private Integer textLength;
    private Integer punctuationCount;
    private Boolean ratioOk;
    private String reason;
    private String problemFragment;
    /** JSON 数组：图片引用（attachment_id/object_key）。 */
    private String evidenceRefs;
    private BigDecimal confidence;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public Integer getTextLength() { return textLength; }
    public void setTextLength(Integer textLength) { this.textLength = textLength; }
    public Integer getPunctuationCount() { return punctuationCount; }
    public void setPunctuationCount(Integer punctuationCount) { this.punctuationCount = punctuationCount; }
    public Boolean getRatioOk() { return ratioOk; }
    public void setRatioOk(Boolean ratioOk) { this.ratioOk = ratioOk; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getProblemFragment() { return problemFragment; }
    public void setProblemFragment(String problemFragment) { this.problemFragment = problemFragment; }
    public String getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(String evidenceRefs) { this.evidenceRefs = evidenceRefs; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
