package com.company.cps.domain;

/**
 * AI 初审任务状态（PRD §28.4）。Java 侧是唯一状态真相源；Python 侧仅执行、不裁决。
 * 界面三态口径：运行中=RUNNING；执行失败=FAILED（立即可接管）；超时可接管=TIMEOUT_OPEN。
 */
public enum CpsInitialReviewTaskStatus {
    /** 初审执行中（投递成功，等待结果或超时）。 */
    RUNNING,
    /** 投递失败或执行异常回调（立即可接管，不等 10 分钟）。 */
    FAILED,
    /** 超时未返回（满 10 分钟；≠执行失败，可接管）。 */
    TIMEOUT_OPEN,
    /** 正常返回结果。 */
    COMPLETED,
    /** 审核专员已人工接管。 */
    TAKEN_OVER,
    /** 接管后迟到结果到达（仅留痕，不覆盖裁决不推进流程）。 */
    LATE_RESULT
}
