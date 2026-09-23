package com.company.cps.dto;

import com.company.cps.domain.CpsInspectionPlanTask;

import java.util.ArrayList;
import java.util.List;

/** B3 mobile 点检任务列表项：计划任务 + 覆盖房间与完成进度。 */
public class CpsRoomCheckTaskResponse {
    private Long taskId;
    private Long planId;
    private String title;
    private String taskStatus;
    private String targetEmpNo;
    private String scheduledAt;
    private String frequency;
    private String acceptanceCriteria;
    private String evidenceRequirement;
    private List<String> roomCodes = new ArrayList<>();
    private int judgedRoomCount;

    public static List<String> parseRoomCodes(String referenceObjectKey) {
        List<String> codes = new ArrayList<>();
        if (referenceObjectKey != null) {
            for (String part : referenceObjectKey.split(",")) {
                String code = part.trim();
                if (!code.isEmpty()) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getTargetEmpNo() { return targetEmpNo; }
    public void setTargetEmpNo(String targetEmpNo) { this.targetEmpNo = targetEmpNo; }
    public String getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(String scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
    public String getEvidenceRequirement() { return evidenceRequirement; }
    public void setEvidenceRequirement(String evidenceRequirement) { this.evidenceRequirement = evidenceRequirement; }
    public List<String> getRoomCodes() { return roomCodes; }
    public void setRoomCodes(List<String> roomCodes) { this.roomCodes = roomCodes; }
    public int getJudgedRoomCount() { return judgedRoomCount; }
    public void setJudgedRoomCount(int judgedRoomCount) { this.judgedRoomCount = judgedRoomCount; }
}
