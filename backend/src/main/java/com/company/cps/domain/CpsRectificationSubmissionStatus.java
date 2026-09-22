package com.company.cps.domain;

/** 整改提交版本状态（详细设计 §2.2(b)）。 */
public enum CpsRectificationSubmissionStatus {
    /** 版本锁定：初审中/待人工审核，不可修改（PRD §28.3）。 */
    LOCKED,
    /** 已有人工裁决（通过关闭或退回）。 */
    REVIEWED,
    /** 已被更新版本取代。 */
    SUPERSEDED
}
