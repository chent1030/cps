package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * B6 视觉点检事件流水（cps_vision_check_judge_event，12 类状态转换流水）。
 * 对应 cps_initial_review_event 模式：append-only、按 judge_fingerprint 串联。
 */
public class CpsVisionCheckJudgeEvent {

    public static final String EVENT_SUBMITTED = "SUBMITTED";
    public static final String EVENT_AI_STARTED = "AI_STARTED";
    public static final String EVENT_AI_COMPLETED = "AI_COMPLETED";
    public static final String EVENT_AI_FAILED = "AI_FAILED";
    public static final String EVENT_TIMEOUT_OPENED = "TIMEOUT_OPENED";
    public static final String EVENT_HUMAN_OVERRIDE = "HUMAN_OVERRIDE";
    public static final String EVENT_REJUDGED = "REJUDGED";

    private Long id;
    private String judgeFingerprint;
    private String eventType;
    private String detail;
    private String operatorEmpNo;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJudgeFingerprint() { return judgeFingerprint; }
    public void setJudgeFingerprint(String judgeFingerprint) { this.judgeFingerprint = judgeFingerprint; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}