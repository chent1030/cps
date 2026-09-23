package com.company.cps.domain;

/** C-04 两阶段判定结果（契约：TYPE_MISMATCH 须重拍 / JUDGED(PASS|FAIL) / UNJUDGEABLE 须补拍）。 */
public enum CpsRoomCheckJudgeOutcome {
    /** 照片类型与点检项类型不符：须重拍，不进入合格判断、不扣分（PRD 24.1/AC-08）。 */
    TYPE_MISMATCH,
    /** 图片模糊/遮挡/缺区域，无法判定：须补拍，不扣分（PRD 24.1/AC-09）。 */
    UNJUDGEABLE,
    /** 判定合格：不扣分。 */
    PASS,
    /** 判定不合格：扣该项 deduct_score。 */
    FAIL,
    /** 降级：Python C-04 未起/超时/异常，暂不判定（不阻塞流程，联调留 J 线）。 */
    PENDING
}
