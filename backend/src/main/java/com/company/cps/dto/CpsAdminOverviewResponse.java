package com.company.cps.dto;

import java.util.List;

/**
 * 管理端概览指标，仅包含真实业务表聚合结果。
 */
public class CpsAdminOverviewResponse {
    private long openIssueCount;
    private long pendingFeedbackCount;
    private long pendingRectifyCount;
    private long pendingReviewCount;
    private long overdueCount;
    private long closedThisMonthCount;
    private List<CpsIssueListItemResponse> recentIssues;

    public long getOpenIssueCount() { return openIssueCount; }
    public void setOpenIssueCount(long openIssueCount) { this.openIssueCount = openIssueCount; }
    public long getPendingFeedbackCount() { return pendingFeedbackCount; }
    public void setPendingFeedbackCount(long pendingFeedbackCount) { this.pendingFeedbackCount = pendingFeedbackCount; }
    public long getPendingRectifyCount() { return pendingRectifyCount; }
    public void setPendingRectifyCount(long pendingRectifyCount) { this.pendingRectifyCount = pendingRectifyCount; }
    public long getPendingReviewCount() { return pendingReviewCount; }
    public void setPendingReviewCount(long pendingReviewCount) { this.pendingReviewCount = pendingReviewCount; }
    public long getOverdueCount() { return overdueCount; }
    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }
    public long getClosedThisMonthCount() { return closedThisMonthCount; }
    public void setClosedThisMonthCount(long closedThisMonthCount) { this.closedThisMonthCount = closedThisMonthCount; }
    public List<CpsIssueListItemResponse> getRecentIssues() { return recentIssues; }
    public void setRecentIssues(List<CpsIssueListItemResponse> recentIssues) { this.recentIssues = recentIssues; }
}
