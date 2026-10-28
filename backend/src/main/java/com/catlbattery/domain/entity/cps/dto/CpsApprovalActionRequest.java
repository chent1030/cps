package com.catlbattery.domain.entity.cps.dto;

import com.catlbattery.domain.entity.cps.CpsApprovalAction;

/**
 * 审批动作请求（approve / reject / withdraw / transfer 通用）。
 * 由 controller 的 @RequestParam 或 @RequestBody 注入。
 */
public class CpsApprovalActionRequest {
    private CpsApprovalAction action;
    private String operatorEmpNo;
    private String operatorEmpName;
    private String comment;

    public CpsApprovalAction getAction() { return action; }
    public void setAction(CpsApprovalAction action) { this.action = action; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public String getOperatorEmpName() { return operatorEmpName; }
    public void setOperatorEmpName(String operatorEmpName) { this.operatorEmpName = operatorEmpName; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
