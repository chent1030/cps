package com.company.cps.domain;

/** D3 计划任务类型（PRD §22.1/§22.2：区域巡检/辅房点检/整改复查三类）。 */
public enum CpsInspectionPlanTaskType {
    /** 整改复查任务（关联 cps_issue.id）。 */
    INSPECT_RECTIFY,
    /** 区域巡检任务。 */
    INSPECT_PATROL,
    /** 辅房点检任务。 */
    INSPECT_CHECK
}
