package com.company.cps.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** B7 周报数据源读端点（PRD §21.1/§21.3；后端架构 §2.1）。
 * 视图 weekly_report_data_* 由 V20260927 创建；本接口仅做条件筛选与窗口投影，
 * 不在 Java 侧做语义汇总。
 */
public interface CpsWeeklyReportDataMapper {

    /** §21.1.6 窗口 [period_start, period_end)；period_end 不含。 */
    List<Map<String, Object>> findIssueSummary(
            @Param("factory") String factory,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    List<Map<String, Object>> findRectificationSummary(
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    List<Map<String, Object>> findInitialReviewSummary(
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    List<Map<String, Object>> findCheckItemSnapshot();

    List<Map<String, Object>> findInventoryLowStock();
}
