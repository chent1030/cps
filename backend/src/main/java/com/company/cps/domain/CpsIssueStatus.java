package com.company.cps.domain;

public enum CpsIssueStatus {
    PENDING_FEEDBACK,
    PENDING_RECTIFY,
    PENDING_UPLOAD_PROOF,
    PENDING_REVIEW,
    CLOSED,
    /** V2 整改域：已提交整改，AI 初审中（版本锁定，不可编辑/转办）。 */
    PENDING_AI_REVIEW,
    /** V2 整改域：缺审核员配置（AC-25：提交保留，待配置后继续流转，不要求重新提交）。 */
    PENDING_REVIEWER_CONFIG
}
