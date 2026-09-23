package com.company.cps.dto;

import java.util.List;

/**
 * 波次7 J线联调（C-04 清单⑤）：PENDING 降级单补判定重跑结果（admin POST /api/cps/admin/room-check-records/{id}/rejudge）。
 * judgeStatus=SUCCESS 表示补判定完成并已回写分数；PENDING 表示仍降级或出现重拍类结果（retakeRequired 列出），可再次重跑。
 */
public class CpsRoomCheckRejudgeResponse {
    private Long recordId;
    private Integer attempt;
    private String judgeStatus;
    private Integer score;
    private List<String> retakeRequired;
    private List<ItemResult> results;

    /** 单明细补判定结果（itemCode 定位，admin 无须感知内部 checkItemId）。 */
    public static class ItemResult {
        public String itemCode;
        public String outcome;
        public String reason;

        public ItemResult() {
        }

        public ItemResult(String itemCode, String outcome, String reason) {
            this.itemCode = itemCode;
            this.outcome = outcome;
            this.reason = reason;
        }
    }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public String getJudgeStatus() { return judgeStatus; }
    public void setJudgeStatus(String judgeStatus) { this.judgeStatus = judgeStatus; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public List<String> getRetakeRequired() { return retakeRequired; }
    public void setRetakeRequired(List<String> retakeRequired) { this.retakeRequired = retakeRequired; }
    public List<ItemResult> getResults() { return results; }
    public void setResults(List<ItemResult> results) { this.results = results; }
}
