package com.company.cps.domain;

/** D1 巡检计划审核状态（PRD §22.2；AC-30：批准后立即创建三类任务）。 */
public enum CpsInspectionPlanStatus {
    /** 待人工审核（C-07 投递草稿后落点）。 */
    PENDING_REVIEW,
    /** 已批准：触发 C-09 建单引擎事务创建三类任务。 */
    APPROVED,
    /** 已拒绝：保留记录不留任务。 */
    REJECTED
}
