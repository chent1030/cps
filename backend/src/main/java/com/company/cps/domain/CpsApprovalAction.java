package com.company.cps.domain;

/**
 * 审批动作（cps_approval_step.action）。
 */
public enum CpsApprovalAction {
    /** 提交人提交 */
    SUBMIT,
    /** 审批人批准 */
    APPROVE,
    /** 审批人驳回 */
    REJECT,
    /** 转交给其他审批人（V2 暂未实现 UI） */
    TRANSFER,
    /** 提交人撤回 */
    WITHDRAW
}
