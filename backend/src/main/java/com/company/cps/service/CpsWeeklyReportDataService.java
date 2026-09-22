package com.company.cps.service;

import com.company.cps.mapper.CpsWeeklyReportDataMapper;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * B7 分类周报数据源（PRD §21.1/§21.3；后端架构 §2.1）。
 *
 * <p>Java 侧仅做窗口投影与视图查询（不做语义汇总、不做周报生成），
 * 供 Python 报告 Agent 调用以拉取按类型/窗口统计的事实表快照。
 *
 * <p>窗口语义（§21.1.6，东八区）：
 * <ul>
 *   <li>本周一 00:00 之前，上周[周一 00:00, 本周一 00:00)；左闭右开。</li>
 *   <li>{@link #currentWeekWindow()} 返回东八区"上一完整自然周"区间；缺省本周一为基准。</li>
 *   <li>调用方可显式传入 periodStart/periodEnd 覆盖。</li>
 * </ul>
 */
@Service
public class CpsWeeklyReportDataService {

    /** 东八区时区（§21.1.6：每周一上午 08:00 自动执行）。 */
    public static final ZoneId ZONE_ASH_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final CpsWeeklyReportDataMapper dataMapper;

    public CpsWeeklyReportDataService(CpsWeeklyReportDataMapper dataMapper) {
        this.dataMapper = dataMapper;
    }

    /** 计算东八区"上一完整自然周"窗口：[上周一 00:00, 本周一 00:00)。 */
    public Window currentWeekWindow() {
        LocalDate todayShanghai = LocalDate.now(ZONE_ASH_SHANGHAI);
        LocalDate thisMonday = todayShanghai.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        return new Window(
                LocalDateTime.of(lastMonday, LocalTime.MIN),
                LocalDateTime.of(thisMonday, LocalTime.MIN));
    }

    public List<Map<String, Object>> getIssueSummary(String factory,
                                                     LocalDateTime periodStart, LocalDateTime periodEnd) {
        return safe(dataMapper.findIssueSummary(factory, periodStart, periodEnd));
    }

    public List<Map<String, Object>> getRectificationSummary(LocalDateTime periodStart, LocalDateTime periodEnd) {
        return safe(dataMapper.findRectificationSummary(periodStart, periodEnd));
    }

    public List<Map<String, Object>> getInitialReviewSummary(LocalDateTime periodStart, LocalDateTime periodEnd) {
        return safe(dataMapper.findInitialReviewSummary(periodStart, periodEnd));
    }

    public List<Map<String, Object>> getCheckItemSnapshot() {
        return safe(dataMapper.findCheckItemSnapshot());
    }

    public List<Map<String, Object>> getInventoryLowStock() {
        return safe(dataMapper.findInventoryLowStock());
    }

    private static List<Map<String, Object>> safe(List<Map<String, Object>> rows) {
        return rows == null ? Collections.emptyList() : rows;
    }

    /** 窗口值对象：periodStart 含 / periodEnd 不含（§21.1.6）。 */
    public static class Window {
        private final LocalDateTime periodStart;
        private final LocalDateTime periodEnd;

        public Window(LocalDateTime periodStart, LocalDateTime periodEnd) {
            if (periodStart == null || periodEnd == null) {
                throw new IllegalArgumentException("periodStart and periodEnd are required");
            }
            if (!periodStart.isBefore(periodEnd)) {
                throw new IllegalArgumentException("periodStart must be before periodEnd (window left-closed right-open)");
            }
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }
        public LocalDateTime getPeriodStart() { return periodStart; }
        public LocalDateTime getPeriodEnd() { return periodEnd; }
    }
}
