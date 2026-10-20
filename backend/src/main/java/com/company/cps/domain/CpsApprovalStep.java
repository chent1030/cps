package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * 审批步骤历史（每次动作追加一条）。
 * seq 从 1 开始单调递增：1=SUBMIT，2=首次 APPROVE/REJECT，3=再次 ...
 */
public class CpsApprovalStep {
    private Long id;
    private Long instanceId;
    private Integer seq;
    private String actorEmpNo;
    private String actorEmpName;
    private CpsApprovalAction action;
    private String comment;
    private CpsApprovalInstanceStatus fromStatus;
    private CpsApprovalInstanceStatus toStatus;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getActorEmpNo() { return actorEmpNo; }
    public void setActorEmpNo(String actorEmpNo) { this.actorEmpNo = actorEmpNo; }
    public String getActorEmpName() { return actorEmpName; }
    public void setActorEmpName(String actorEmpName) { this.actorEmpName = actorEmpName; }
    public CpsApprovalAction getAction() { return action; }
    public void setAction(CpsApprovalAction action) { this.action = action; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public CpsApprovalInstanceStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(CpsApprovalInstanceStatus fromStatus) { this.fromStatus = fromStatus; }
    public CpsApprovalInstanceStatus getToStatus() { return toStatus; }
    public void setToStatus(CpsApprovalInstanceStatus toStatus) { this.toStatus = toStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
