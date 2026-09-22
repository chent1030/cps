package com.company.cps.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * C-02 回调请求体：Python → Java POST /api/callbacks/initial-review/result。
 * 字段名与详细设计 §3.2 契约一致（snake_case）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CpsInitialReviewCallbackRequest {

    /** 任务定位键：数字主键字符串，或 Python 侧任务引用 cps-rectify-{issueId}-v{n}（J0 联调修正：Python 只持有字符串引用）。 */
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("submission_id")
    private Long submissionId;

    @JsonProperty("issue_id")
    private Long issueId;

    @JsonProperty("version_no")
    private Integer versionNo;

    /** 幂等键 initial-review-result-{taskId}。 */
    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    /** 模型执行状态（如 SUCCEEDED/FAILED）。 */
    @JsonProperty("model_status")
    private String modelStatus;

    /** 总览意见：PASS/PARTIAL/PROBLEM（仅意见，无决定权）。 */
    private String overall;

    /** 执行失败回调：错误码（非空即视为失败回调）。 */
    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    private List<Item> items;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        /** IMAGE_COMPARE/MEASURE_SIMILARITY/TEXT_VALIDITY/TEXT_LENGTH/PUNCTUATION_RATIO。 */
        @JsonProperty("check_type")
        private String checkType;

        /** reason/short_term/long_term。 */
        @JsonProperty("field_name")
        private String fieldName;

        /** PASS/FAIL/WARN/SKIPPED。 */
        private String verdict;

        /** L：文本长度实际值（含空格标点，换行不计）。 */
        @JsonProperty("text_length")
        private Integer textLength;

        /** P：标点出现次数实际值。 */
        @JsonProperty("punctuation_count")
        private Integer punctuationCount;

        /** 10×P≤L 是否满足。 */
        @JsonProperty("ratio_ok")
        private Boolean ratioOk;

        private String reason;

        @JsonProperty("problem_fragment")
        private String problemFragment;

        /** 图片引用（attachment_id/object_key）。 */
        @JsonProperty("evidence_refs")
        private List<Object> evidenceRefs;

        private BigDecimal confidence;

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
        public List<Object> getEvidenceRefs() { return evidenceRefs; }
        public void setEvidenceRefs(List<Object> evidenceRefs) { this.evidenceRefs = evidenceRefs; }
        public BigDecimal getConfidence() { return confidence; }
        public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getModelStatus() { return modelStatus; }
    public void setModelStatus(String modelStatus) { this.modelStatus = modelStatus; }
    public String getOverall() { return overall; }
    public void setOverall(String overall) { this.overall = overall; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
}
