package com.company.cps.dto;

import java.util.List;

/**
 * 管理端通用分页响应。
 */
public class CpsAdminPageResponse<T> {
    private List<T> records;
    private long total;
    private int page;
    private int pageSize;

    public CpsAdminPageResponse(List<T> records, long total, int page, int pageSize) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> getRecords() { return records; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}
