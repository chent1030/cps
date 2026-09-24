package com.company.cps.dto;

import com.company.cps.domain.CpsVisionCheckRecord;

/**
 * B6 视觉点检提交/改判/重判返回（POST 返回 record + judge_fingerprint）。
 * 单一 record 落地便于 admin 复盘（judgeFingerprint 用于事件流水串联）。
 */
public class CpsVisionCheckSubmitResult {
    private CpsVisionCheckRecord record;
    private String judgeFingerprint;

    public CpsVisionCheckSubmitResult() {}

    public CpsVisionCheckSubmitResult(CpsVisionCheckRecord record, String judgeFingerprint) {
        this.record = record;
        this.judgeFingerprint = judgeFingerprint;
    }

    public CpsVisionCheckRecord getRecord() { return record; }
    public void setRecord(CpsVisionCheckRecord record) { this.record = record; }
    public String getJudgeFingerprint() { return judgeFingerprint; }
    public void setJudgeFingerprint(String judgeFingerprint) { this.judgeFingerprint = judgeFingerprint; }
}