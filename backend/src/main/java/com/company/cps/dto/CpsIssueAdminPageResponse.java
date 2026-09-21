package com.company.cps.dto;

import java.util.List;

/**
 * 管理端问题分页查询结果。
 */
public class CpsIssueAdminPageResponse {
    private List<CpsIssueListItemResponse> records;
    private long total;
    private int page;
    private int pageSize;

    public CpsIssueAdminPageResponse(List<CpsIssueListItemResponse> records, long total, int page, int pageSize) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<CpsIssueListItemResponse> getRecords() { return records; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}
