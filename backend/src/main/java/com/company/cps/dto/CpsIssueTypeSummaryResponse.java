package com.company.cps.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页/筛选器的巡检类型聚合：当前页签下按巡检类型分组的条数。
 * 客户端配合 GET /api/cps/inspection-types 使用，可为没有数据的类型补 0。
 */
public class CpsIssueTypeSummaryResponse {

    private long total;
    private List<TypeCount> types = new ArrayList<>();

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public List<TypeCount> getTypes() { return types; }
    public void setTypes(List<TypeCount> types) { this.types = types; }

    public void addType(String code, String name, long count) {
        TypeCount item = new TypeCount();
        item.setCode(code);
        item.setName(name);
        item.setCount(count);
        this.types.add(item);
        this.total += count;
    }

    /** 单个巡检类型的条数。 */
    public static class TypeCount {
        private String code;
        private String name;
        private long count;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}
