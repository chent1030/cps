package com.company.cps.domain;

public enum CpsIssueAction {
    SUBMIT,
    REPLY_ASSIGN,
    RECTIFY,
    UPLOAD_PROOF,
    REVIEW_CLOSE,
    REVIEW_REJECT,
    TRANSFER,
    /** V2 整改域：提交整改（原因+短期/长期措施+整改照片，触发 AI 初审）。 */
    SUBMIT_RECTIFICATION,
    /** V2 整改域：暂存（允许不完整内容，不触发初审，不生成版本）。 */
    SAVE_DRAFT,
    /** V2 系统事件（不进入 availableActions）：AI 初审终态推进（结果就绪/执行失败/超时可接管）。 */
    AI_REVIEW_ADVANCE,
    /** V2 系统事件（不进入 availableActions）：审核员配置完成，待配置单继续流转（AC-25）。 */
    REVIEWER_CONFIGURED,
    /**
     * V2 管理动作（不进入 availableActions，A2/AC-25）：管理员改配审核员（PRD §30.2）。
     * 未完成审核单转新审核员，原审核员失权；不重置 AI 初审计时；保留变更记录。
     */
    REVIEWER_REASSIGN
}
