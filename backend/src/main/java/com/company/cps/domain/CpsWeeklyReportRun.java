package com.company.cps.domain;

import java.time.LocalDateTime;

/** C-05 周报运行记录（PRD §21.3；后端架构 §2.3）。
 * Java 侧不持久化该表（数据源在 Python PG），admin 端经 C-08 转发查询/下载。
 * 此处仅做序列化契约承载。
 */
public class CpsWeeklyReportRun {
    private String runId;
    private String runNo;
    private String inspectionType;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
    private String failStage;
    private String failReason;
    private String fileName;
    private String fileKey;
    private Long fileSize;
    private String sourcePlanRef;
    private String pushStatus;
    private String pushChannel;
    private LocalDateTime pushedAt;
    private Integer retryCount;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getRunNo() { return runNo; }
    public void setRunNo(String runNo) { this.runNo = runNo; }
    public String getInspectionType() { return inspectionType; }
    public void setInspectionType(String inspectionType) { this.inspectionType = inspectionType; }
    public LocalDateTime getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDateTime periodStart) { this.periodStart = periodStart; }
    public LocalDateTime getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDateTime periodEnd) { this.periodEnd = periodEnd; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailStage() { return failStage; }
    public void setFailStage(String failStage) { this.failStage = failStage; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSourcePlanRef() { return sourcePlanRef; }
    public void setSourcePlanRef(String sourcePlanRef) { this.sourcePlanRef = sourcePlanRef; }
    public String getPushStatus() { return pushStatus; }
    public void setPushStatus(String pushStatus) { this.pushStatus = pushStatus; }
    public String getPushChannel() { return pushChannel; }
    public void setPushChannel(String pushChannel) { this.pushChannel = pushChannel; }
    public LocalDateTime getPushedAt() { return pushedAt; }
    public void setPushedAt(LocalDateTime pushedAt) { this.pushedAt = pushedAt; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
}
