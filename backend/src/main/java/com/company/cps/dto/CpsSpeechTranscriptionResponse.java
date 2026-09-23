package com.company.cps.dto;

/** F3 语音转写响应（mobile 回填表单用；转写文本不作证据）。 */
public class CpsSpeechTranscriptionResponse {
    /** TRANSCRIBED / SKIPPED / UNAVAILABLE（降级）。 */
    private String status;
    /** 转写文本：SKIPPED 恒空串；UNAVAILABLE 为 null（前端展示 fallbackMessage）。 */
    private String text;
    /** SKIPPED/UNAVAILABLE 时的提示文案（失败降级文案，可重试或手工输入）。 */
    private String fallbackMessage;
    private String field;
    private Integer attempt;
    private String submissionId;
    private String audioObjectKey;
    private Double durationSeconds;
    private String model;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getFallbackMessage() { return fallbackMessage; }
    public void setFallbackMessage(String fallbackMessage) { this.fallbackMessage = fallbackMessage; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }
    public String getAudioObjectKey() { return audioObjectKey; }
    public void setAudioObjectKey(String audioObjectKey) { this.audioObjectKey = audioObjectKey; }
    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
