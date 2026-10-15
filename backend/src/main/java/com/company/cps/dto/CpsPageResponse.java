package com.company.cps.dto;

import java.util.List;

/** 通用分页响应（total + rows；与 admin issues 分页约定一致）。 */
public class CpsPageResponse<T> {
    private long total;
    private List<T> rows;

    public CpsPageResponse(long total, List<T> rows) {
        this.total = total;
        this.rows = rows;
    }

    public long getTotal() { return total; }
    public List<T> getRows() { return rows; }
}
