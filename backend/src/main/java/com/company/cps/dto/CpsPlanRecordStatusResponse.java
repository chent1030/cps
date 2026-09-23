package com.company.cps.dto;

import java.time.LocalDateTime;
import java.util.List;

/** D4 计划建单记录状态聚合视图（AC-06/30）：计划→建单结果→任务状态一屏可查，补建/失败可追溯。 */
public class CpsPlanRecordStatusResponse {

    private Long planId;
    private String planTitle;
    private String planStatus;
    private LocalDateTime approvedAt;
    private List<TypeRecordStatus> perType;
    private Summary summary;

    public static class TypeRecordStatus {
        private String taskType;
        private boolean taskExists;
        private Long taskId;
        private String taskStatus;
        /** 最近一次建单尝试结果：CREATED/SKIPPED_EXISTING/CREATE_FAILED；null=从未尝试 */
        private String lastBuildResult;
        private String lastBuildErrorMsg;
        private LocalDateTime lastBuildAt;
        private String lastBuildSource;

        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public boolean isTaskExists() { return taskExists; }
        public void setTaskExists(boolean taskExists) { this.taskExists = taskExists; }
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getTaskStatus() { return taskStatus; }
        public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
        public String getLastBuildResult() { return lastBuildResult; }
        public void setLastBuildResult(String lastBuildResult) { this.lastBuildResult = lastBuildResult; }
        public String getLastBuildErrorMsg() { return lastBuildErrorMsg; }
        public void setLastBuildErrorMsg(String lastBuildErrorMsg) { this.lastBuildErrorMsg = lastBuildErrorMsg; }
        public LocalDateTime getLastBuildAt() { return lastBuildAt; }
        public void setLastBuildAt(LocalDateTime lastBuildAt) { this.lastBuildAt = lastBuildAt; }
        public String getLastBuildSource() { return lastBuildSource; }
        public void setLastBuildSource(String lastBuildSource) { this.lastBuildSource = lastBuildSource; }
    }

    public static class Summary {
        private int typesCreated;
        private int typesFailed;
        private int typesMissing;

        public int getTypesCreated() { return typesCreated; }
        public void setTypesCreated(int typesCreated) { this.typesCreated = typesCreated; }
        public int getTypesFailed() { return typesFailed; }
        public void setTypesFailed(int typesFailed) { this.typesFailed = typesFailed; }
        public int getTypesMissing() { return typesMissing; }
        public void setTypesMissing(int typesMissing) { this.typesMissing = typesMissing; }
        public boolean isAllBuilt() { return typesFailed == 0 && typesMissing == 0; }
        public boolean getAllBuilt() { return isAllBuilt(); }
    }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanTitle() { return planTitle; }
    public void setPlanTitle(String planTitle) { this.planTitle = planTitle; }
    public String getPlanStatus() { return planStatus; }
    public void setPlanStatus(String planStatus) { this.planStatus = planStatus; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public List<TypeRecordStatus> getPerType() { return perType; }
    public void setPerType(List<TypeRecordStatus> perType) { this.perType = perType; }
    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }
}
