package com.company.cps.domain;

import java.time.LocalDateTime;

/** D4 计划建单记录（cps_plan_build_record，append-only；AC-30 失败仅补建可追溯）。 */
public class CpsPlanBuildRecord {
    public static final String RESULT_CREATED = "CREATED";
    public static final String RESULT_SKIPPED_EXISTING = "SKIPPED_EXISTING";
    public static final String RESULT_CREATE_FAILED = "CREATE_FAILED";

    public static final String SOURCE_APPROVE = "APPROVE";
    public static final String SOURCE_REBUILD = "REBUILD";

    private Long id;
    private Long planId;
    private String taskType;
    private String result;
    private String errorMsg;
    private String builtBy;
    private String buildSource;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public String getBuiltBy() { return builtBy; }
    public void setBuiltBy(String builtBy) { this.builtBy = builtBy; }
    public String getBuildSource() { return buildSource; }
    public void setBuildSource(String buildSource) { this.buildSource = buildSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
