package com.catlbattery.domain.entity.cps;

import java.time.LocalDateTime;

/**
 * 统一巡检记录（Phase C1）：一次执行一条；JUDGED 后锁定。
 * 分区域类型 areaId 必填；不分区域类型为 NULL（按 typeCode 直挂点检项）。
 */
public class CpsInspectionRecord {

    private Long id;
    private String typeCode;
    private Long areaId;
    private String areaCode;
    private String areaName;
    private Long planTaskId;
    private Long planId;
    private String checkEmpNo;
    private String checkEmpName;
    /** PENDING/IN_PROGRESS/AWAIT_CONFIRM/JUDGED */
    private String recordStatus;
    /** AI判定服务状态：SUCCESS/PENDING（降级） */
    private String judgeStatus;
    private Integer score;
    /** AI 初审（C-04 视觉判定 + 扣分策略）：PASS/PARTIAL/PROBLEM/NONE */
    private String aiOverall;
    private Integer aiScore;
    private String aiReason;
    private LocalDateTime aiJudgedAt;
    /** 提交-复核轮数（退回后 +1，作为 AI 幂等键的一部分） */
    private Integer reviewRound;
    private String reviewEmpNo;
    private String reviewEmpName;
    private String reviewOpinion;
    private LocalDateTime reviewedAt;
    /** 整改责任人（分区域取 cps_inspection_area.keeper_emp_no，否则回退检查人） */
    private String rectifyEmpNo;
    private String rectifyEmpName;
    private String rectifyReason;
    private String rectifyShortTerm;
    private String rectifyLongTerm;
    /** 整改照片：cps_issue_attachment.id 逗号分隔 */
    private String rectifyAttachmentIds;
    private LocalDateTime rectifySubmittedAt;
    /** 整改提交 C-01 初审结论 */
    private String rectifyAiOverall;
    private String rectifyAiReason;
    private String currentHandlerEmpNo;
    private String currentHandlerEmpName;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    /** 迁移来源 cps_room_check_record.id */
    private Long sourceRecordId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public Long getPlanTaskId() { return planTaskId; }
    public void setPlanTaskId(Long planTaskId) { this.planTaskId = planTaskId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getCheckEmpNo() { return checkEmpNo; }
    public void setCheckEmpNo(String checkEmpNo) { this.checkEmpNo = checkEmpNo; }
    public String getCheckEmpName() { return checkEmpName; }
    public void setCheckEmpName(String checkEmpName) { this.checkEmpName = checkEmpName; }
    public String getRecordStatus() { return recordStatus; }
    public void setRecordStatus(String recordStatus) { this.recordStatus = recordStatus; }
    public String getJudgeStatus() { return judgeStatus; }
    public void setJudgeStatus(String judgeStatus) { this.judgeStatus = judgeStatus; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getAiOverall() { return aiOverall; }
    public void setAiOverall(String aiOverall) { this.aiOverall = aiOverall; }
    public Integer getAiScore() { return aiScore; }
    public void setAiScore(Integer aiScore) { this.aiScore = aiScore; }
    public String getAiReason() { return aiReason; }
    public void setAiReason(String aiReason) { this.aiReason = aiReason; }
    public LocalDateTime getAiJudgedAt() { return aiJudgedAt; }
    public void setAiJudgedAt(LocalDateTime aiJudgedAt) { this.aiJudgedAt = aiJudgedAt; }
    public Integer getReviewRound() { return reviewRound; }
    public void setReviewRound(Integer reviewRound) { this.reviewRound = reviewRound; }
    public String getReviewEmpNo() { return reviewEmpNo; }
    public void setReviewEmpNo(String reviewEmpNo) { this.reviewEmpNo = reviewEmpNo; }
    public String getReviewEmpName() { return reviewEmpName; }
    public void setReviewEmpName(String reviewEmpName) { this.reviewEmpName = reviewEmpName; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String reviewOpinion) { this.reviewOpinion = reviewOpinion; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRectifyEmpNo() { return rectifyEmpNo; }
    public void setRectifyEmpNo(String rectifyEmpNo) { this.rectifyEmpNo = rectifyEmpNo; }
    public String getRectifyEmpName() { return rectifyEmpName; }
    public void setRectifyEmpName(String rectifyEmpName) { this.rectifyEmpName = rectifyEmpName; }
    public String getRectifyReason() { return rectifyReason; }
    public void setRectifyReason(String rectifyReason) { this.rectifyReason = rectifyReason; }
    public String getRectifyShortTerm() { return rectifyShortTerm; }
    public void setRectifyShortTerm(String rectifyShortTerm) { this.rectifyShortTerm = rectifyShortTerm; }
    public String getRectifyLongTerm() { return rectifyLongTerm; }
    public void setRectifyLongTerm(String rectifyLongTerm) { this.rectifyLongTerm = rectifyLongTerm; }
    public String getRectifyAttachmentIds() { return rectifyAttachmentIds; }
    public void setRectifyAttachmentIds(String rectifyAttachmentIds) { this.rectifyAttachmentIds = rectifyAttachmentIds; }
    public LocalDateTime getRectifySubmittedAt() { return rectifySubmittedAt; }
    public void setRectifySubmittedAt(LocalDateTime rectifySubmittedAt) { this.rectifySubmittedAt = rectifySubmittedAt; }
    public String getRectifyAiOverall() { return rectifyAiOverall; }
    public void setRectifyAiOverall(String rectifyAiOverall) { this.rectifyAiOverall = rectifyAiOverall; }
    public String getRectifyAiReason() { return rectifyAiReason; }
    public void setRectifyAiReason(String rectifyAiReason) { this.rectifyAiReason = rectifyAiReason; }
    public String getCurrentHandlerEmpNo() { return currentHandlerEmpNo; }
    public void setCurrentHandlerEmpNo(String currentHandlerEmpNo) { this.currentHandlerEmpNo = currentHandlerEmpNo; }
    public String getCurrentHandlerEmpName() { return currentHandlerEmpName; }
    public void setCurrentHandlerEmpName(String currentHandlerEmpName) { this.currentHandlerEmpName = currentHandlerEmpName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
