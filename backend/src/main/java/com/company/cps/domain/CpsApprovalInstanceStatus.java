package com.company.cps.domain;

/**
 * 通用审批实例状态。
 * V1 用于 cps_inspection_plan（PENDING_REVIEW ↔ PENDING/APPROVED/REJECTED）。
 */
public enum CpsApprovalInstanceStatus {
    /** 等待审批（业务实体处于 PENDING_REVIEW 等中间态） */
    PENDING,
    /** 已批准 */
    APPROVED,
    /** 已驳回 */
    REJECTED,
    /** 提交方主动撤回 */
    WITHDRAWN
}
