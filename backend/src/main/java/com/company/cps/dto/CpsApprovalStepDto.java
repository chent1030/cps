package com.company.cps.dto;

import com.company.cps.domain.CpsApprovalAction;
import com.company.cps.domain.CpsApprovalInstanceStatus;
import com.company.cps.domain.CpsApprovalStep;

import java.time.LocalDateTime;

public class CpsApprovalStepDto {
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

    public static CpsApprovalStepDto from(CpsApprovalStep e) {
        CpsApprovalStepDto d = new CpsApprovalStepDto();
        d.id = e.getId();
        d.instanceId = e.getInstanceId();
        d.seq = e.getSeq();
        d.actorEmpNo = e.getActorEmpNo();
        d.actorEmpName = e.getActorEmpName();
        d.action = e.getAction();
        d.comment = e.getComment();
        d.fromStatus = e.getFromStatus();
        d.toStatus = e.getToStatus();
        d.createdAt = e.getCreatedAt();
        return d;
    }

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
