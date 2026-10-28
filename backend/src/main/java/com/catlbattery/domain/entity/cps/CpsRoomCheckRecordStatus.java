package com.catlbattery.domain.entity.cps;

/** B3 点检单状态机（任务口径：待执行→执行中→已判定待确认→已确认锁定）。 */
public enum CpsRoomCheckRecordStatus {
    /** 待执行：单已生成（含明细快照），尚无照片。 */
    PENDING,
    /** 执行中：已上传至少一张照片。 */
    IN_PROGRESS,
    /** 已判定待确认：AI 判定成功，等待点检人确认/修正（PRD 24.2.2），未锁定可重拍重判。 */
    AWAIT_CONFIRM,
    /** 已确认锁定：点检人已确认（或降级单由 admin rejudge 补判），锁定不可再改（PRD 24.2.4）。 */
    JUDGED
}
