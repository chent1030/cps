package com.company.cps.dto;

/** B4 周评分明细条目。 */
public class CpsWeeklyScoreLineResponse {

    private Long id;
    private String itemId;
    private Integer scoreDelta;
    private String reason;

    public CpsWeeklyScoreLineResponse() {}

    public CpsWeeklyScoreLineResponse(Long id, String itemId, Integer scoreDelta, String reason) {
        this.id = id;
        this.itemId = itemId;
        this.scoreDelta = scoreDelta;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public Integer getScoreDelta() { return scoreDelta; }
    public void setScoreDelta(Integer scoreDelta) { this.scoreDelta = scoreDelta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
