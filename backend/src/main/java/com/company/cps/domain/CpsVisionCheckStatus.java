package com.company.cps.domain;

/**
 * B6 视觉点检状态枚举（cps_vision_check_record.status）。
 * 状态机：PENDING → AI_JUDGING → AI_PASS/AI_FAIL；TIMEOUT 分支；HUMAN_OVERRIDE 改判。
 * 与 cps_room_check_record_status（B3 PENDING/IN_PROGRESS/JUDGED）不共享。
 */
public enum CpsVisionCheckStatus {
    PENDING,
    AI_JUDGING,
    AI_PASS,
    AI_FAIL,
    HUMAN_OVERRIDE,
    TIMEOUT;

    /** 可人工改判的状态：AI_JUDGING/AI_PASS/AI_FAIL。 */
    public boolean isHumanOverrideAllowed() {
        return this == AI_JUDGING || this == AI_PASS || this == AI_FAIL;
    }

    /** 可重判的状态：PENDING/TIMEOUT（AI_FAILED 暂未在 spec 中出现，按 TIMEOUT 同源处理）。 */
    public boolean isRejudgeAllowed() {
        return this == PENDING || this == TIMEOUT;
    }

    public static boolean isValid(String value) {
        if (value == null) return false;
        for (CpsVisionCheckStatus s : values()) {
            if (s.name().equals(value)) return true;
        }
        return false;
    }
}