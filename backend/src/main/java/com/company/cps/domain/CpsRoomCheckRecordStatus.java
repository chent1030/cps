package com.company.cps.domain;

/** B3 点检单状态机（任务口径：待执行→执行中→已判定）。 */
public enum CpsRoomCheckRecordStatus {
    /** 待执行：单已生成（含明细快照），尚无照片。 */
    PENDING,
    /** 执行中：已上传至少一张照片。 */
    IN_PROGRESS,
    /** 已判定：已提交并完成判定（含降级 PENDING），提交锁定不可再改（PRD 24.2.4）。 */
    JUDGED
}
