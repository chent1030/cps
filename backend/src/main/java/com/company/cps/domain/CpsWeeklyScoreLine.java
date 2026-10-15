package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * B4 周评分明细（cps_weekly_score_line）：每条评分扣/加分明细。
 * item_id 对应 cps_scoring_rule.rule_key（如 base / content_mismatch_deduct）；
 * score_delta 可正可负；reason 记录触发原因（如 "01-15 仓库照片模糊"）。
 */
public class CpsWeeklyScoreLine {

    private Long id;
    private Long weeklyScoreId;
    private String itemId;
    private Integer scoreDelta;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWeeklyScoreId() { return weeklyScoreId; }
    public void setWeeklyScoreId(Long weeklyScoreId) { this.weeklyScoreId = weeklyScoreId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public Integer getScoreDelta() { return scoreDelta; }
    public void setScoreDelta(Integer scoreDelta) { this.scoreDelta = scoreDelta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
