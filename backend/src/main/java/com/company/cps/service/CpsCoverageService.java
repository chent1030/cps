package com.company.cps.service;

import com.company.cps.dto.CpsPageResponse;
import com.company.cps.mapper.CpsCoverageMapper;
import com.company.cps.mapper.CpsEffectMetricMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-10 历史/覆盖分析聚合查询（波次10）：
 * 复用 cps_issue + cps_problem_category + cps_room + cps_room_check_record/cps_inspection_item 的 JOIN，
 * 输出四类覆盖维度聚合行：频率分布 / 区域处理人监控 / 复发分析 / 覆盖缺口。
 *
 * <p>波次11 扩展：
 * <ul>
 *   <li>FR-11 procedural 调度记忆消费端 dispatcherMemoryGroupBy —— 复用 cps_review_adjudication JOIN cps_issue 聚合</li>
 *   <li>FR-12 效果评估指标 effect —— 四 metric (ai_pass_rate / human_override_rate / avg_close_duration_hours / recurrence_rate_30d)</li>
 * </ul>
 *
 * <p>分页语义与 CpsMemoryAdminService 一致：默认 size=200 max=1000（频率维度更高粒度）。
 */
@Service
public class CpsCoverageService {

    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 1000;

    /** 默认超期阈值（与 cps_issue 列口径一致：3 天未处理即超期）。 */
    private static final int DEFAULT_OVERDUE_DAYS = 3;
    /** 默认覆盖缺口窗口：7 天无 cps_room_check_record 即视为缺口。 */
    private static final int DEFAULT_GAP_DAYS = 7;
    /** 默认复发阈值：同一 issue 在窗口内 REOPEN+REJECT 累计 ≥ 2 次。 */
    private static final int DEFAULT_RECURRENCE_THRESHOLD = 2;
    /** 默认复发窗口：90 天。 */
    private static final int DEFAULT_RECURRENCE_WINDOW_DAYS = 90;

    /** FR-11 调度记忆样例 reason 截断 200 字（与 mapper SQL SUBSTRING 对齐）。 */
    private static final int DISPATCHER_SAMPLE_REASON_LEN = 200;
    /** FR-11 调度记忆每聚合组样例条数（与 mapper SQL GROUP_CONCAT 取 5 对齐）。 */
    private static final int DISPATCHER_SAMPLE_COUNT = 5;

    /** FR-12 默认 recurrence 窗口（90 天，与描述一致；metric key 名带 _30d 后缀保留兼容）。 */
    private static final int DEFAULT_EFFECT_RECURRENCE_WINDOW_DAYS = 90;
    /** FR-12 默认 effect 时间窗：30 天（与 _30d 命名一致；periodStart/periodEnd 显式传入时优先）。 */
    private static final int DEFAULT_EFFECT_WINDOW_DAYS = 30;

    private final CpsCoverageMapper coverageMapper;
    private final CpsEffectMetricMapper effectMetricMapper;

    public CpsCoverageService(CpsCoverageMapper coverageMapper,
                              CpsEffectMetricMapper effectMetricMapper) {
        this.coverageMapper = coverageMapper;
        this.effectMetricMapper = effectMetricMapper;
    }

    /**
     * 频率分布：(factory, area, categoryL1Id) 维度聚合 issue_count / recent_count / closed_count / close_rate。
     */
    public CpsPageResponse<Map<String, Object>> frequencyGroupBy(
            String factory, String area, Long categoryL1Id,
            String startTime, String endTime,
            Integer page, Integer size) {
        int[] norm = normalizePage(page, size);
        String factoryArg = trim(factory);
        String areaArg = trim(area);
        String startArg = trim(startTime);
        String endArg = trim(endTime);
        long total = coverageMapper.countFrequencyGroupBy(
                factoryArg, areaArg, categoryL1Id, startArg, endArg);
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : coverageMapper.frequencyGroupBy(
                        factoryArg, areaArg, categoryL1Id, startArg, endArg,
                        norm[1], norm[0]);
        return new CpsPageResponse<>(total, rows);
    }

    /**
     * 区域处理人监控：(area, current_handler_emp_no) 维度聚合 open/handled/overdue。
     * status 默认 __OPEN__（非 CLOSED）；overdueDays 默认 3。
     */
    public CpsPageResponse<Map<String, Object>> regionSupervisorGroupBy(
            String factory, String area, Long categoryL1Id,
            String status, Integer overdueDays,
            Integer page, Integer size) {
        int[] norm = normalizePage(page, size);
        String factoryArg = trim(factory);
        String areaArg = trim(area);
        String statusArg = status == null || status.isEmpty() ? "__OPEN__" : status.trim();
        int overdue = overdueDays == null || overdueDays < 0 ? DEFAULT_OVERDUE_DAYS : overdueDays;
        long total = coverageMapper.countRegionSupervisorGroupBy(
                factoryArg, areaArg, categoryL1Id, statusArg);
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : coverageMapper.regionSupervisorGroupBy(
                        factoryArg, areaArg, categoryL1Id, statusArg, overdue,
                        norm[1], norm[0]);
        return new CpsPageResponse<>(total, rows);
    }

    /**
     * 复发分析：startTime/endTime 默认 90 天窗口，threshold 默认 2。
     */
    public CpsPageResponse<Map<String, Object>> recurrenceGroupBy(
            String startTime, String endTime, Long categoryL1Id,
            Integer threshold, Integer page, Integer size) {
        int[] norm = normalizePage(page, size);
        String startArg = trim(startTime);
        String endArg = trim(endTime);
        int thr = threshold == null || threshold < 1 ? DEFAULT_RECURRENCE_THRESHOLD : threshold;
        if (startArg == null || endArg == null) {
            // 默认 90 天窗口：start = NOW - 90d，end = NOW
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (endArg == null) endArg = now.toString();
            if (startArg == null) startArg = now.minusDays(DEFAULT_RECURRENCE_WINDOW_DAYS).toString();
        }
        long total = coverageMapper.countRecurrenceGroupBy(
                startArg, endArg, categoryL1Id, thr);
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : coverageMapper.recurrenceGroupBy(
                        startArg, endArg, categoryL1Id, thr,
                        norm[1], norm[0]);
        return new CpsPageResponse<>(total, rows);
    }

    /**
     * 覆盖缺口：启用 cps_inspection_item × 启用 cps_room，过去 gapDays 天无 cps_room_check_record 视为缺口。
     */
    public CpsPageResponse<Map<String, Object>> coverageGaps(
            String factory, String area, String storageRoomType, Integer gapDays,
            Integer page, Integer size) {
        int[] norm = normalizePage(page, size);
        String factoryArg = trim(factory);
        String storageArg = trim(storageRoomType);
        int gap = gapDays == null || gapDays < 1 ? DEFAULT_GAP_DAYS : gapDays;
        long total = coverageMapper.countCoverageGaps(
                factoryArg, trim(area), storageArg, gap);
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : coverageMapper.coverageGaps(
                        factoryArg, trim(area), storageArg, gap,
                        norm[1], norm[0]);
        return new CpsPageResponse<>(total, rows);
    }

    /**
     * FR-11 procedural 调度记忆消费端：按 (category_l1_id, area, decision, ai_relation) 维度聚合历史裁决，
     * 每组 sampleReasons 解析为最近 N 条样例结构（reviewer/createdAt/reason）。
     * 任一过滤参数 null/空 = 不过滤；pageSize 默认 200 max 1000。
     */
    public CpsPageResponse<Map<String, Object>> dispatcherMemoryGroupBy(
            Long categoryL1Id, Long categoryL2Id,
            String factory, String area,
            String decision, String aiRelation,
            String startTime, String endTime,
            Integer page, Integer size) {
        int[] norm = normalizePage(page, size);
        Long c1 = categoryL1Id;
        Long c2 = categoryL2Id;
        String f = trim(factory);
        String a = trim(area);
        String d = trim(decision);
        String ar = trim(aiRelation);
        String st = trim(startTime);
        String et = trim(endTime);
        long total = coverageMapper.countDispatcherMemoryGroupBy(
                c1, c2, f, a, d, ar, st, et);
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : coverageMapper.dispatcherMemoryGroupBy(
                        c1, c2, f, a, d, ar, st, et,
                        norm[1], norm[0]);
        // 解析 sampleReasons 字符串 → List<Map<String,String>>
        for (Map<String, Object> row : rows) {
            Object raw = row.get("sampleReasons");
            row.put("sampleReasons", parseSampleReasons(raw));
        }
        return new CpsPageResponse<>(total, rows);
    }

    /**
     * FR-12 效果评估指标查询：四 metric（ai_pass_rate / human_override_rate / avg_close_duration_hours / recurrence_rate_30d）。
     * periodStart/periodEnd 为 null 时默认 30 天窗口；recurrence 窗口默认 90 天（与 Wave 10 recurrenceGroupBy 对齐）。
     * 返回 CpsPageResponse-like 结构（rows: 4 个 metric，每条 metric_key + metric_value + recorded_at）。
     * 字段命名：metricKey / metricValue / recordedAt。
     */
    public CpsPageResponse<Map<String, Object>> effect(
            String periodStart, String periodEnd, String metricKey, String scopeKey) {
        String ps = trim(periodStart);
        String pe = trim(periodEnd);
        String mk = trim(metricKey);
        String sk = trim(scopeKey);
        // 默认 30 天窗口：start = NOW - 30d，end = NOW
        LocalDateTime now = LocalDateTime.now();
        if (pe == null) pe = now.toString();
        if (ps == null) ps = now.minusDays(DEFAULT_EFFECT_WINDOW_DAYS).toString();
        // recurrence 默认 90 天窗口
        LocalDateTime recurStart = now.minusDays(DEFAULT_EFFECT_RECURRENCE_WINDOW_DAYS);

        List<Map<String, Object>> rows = new ArrayList<>(4);
        // 1) ai_pass_rate
        rows.add(buildMetricRow("ai_pass_rate",
                effectMetricMapper.aiPassRate(ps, pe, mk, sk), now));
        // 2) human_override_rate
        rows.add(buildMetricRow("human_override_rate",
                effectMetricMapper.humanOverrideRate(ps, pe, mk, sk), now));
        // 3) avg_close_duration_hours
        Double avgHours = effectMetricMapper.avgCloseDurationHours(ps, pe, mk, sk);
        rows.add(buildMetricRow("avg_close_duration_hours",
                avgHours == null ? null : roundHalfUp(avgHours, 2), now));
        // 4) recurrence_rate_30d（90 天窗口）
        Double recurrence = effectMetricMapper.recurrenceRate(
                recurStart.toString(), now.toString(), mk, sk);
        rows.add(buildMetricRow("recurrence_rate_30d",
                recurrence == null ? null : roundHalfUp(recurrence * 100.0, 2), now));

        // total = rows.size()，便于前端分页感知
        return new CpsPageResponse<>(rows.size(), rows);
    }

    private static Map<String, Object> buildMetricRow(String metricKey, Double metricValue, LocalDateTime recordedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metricKey", metricKey);
        row.put("metricValue", metricValue);
        row.put("recordedAt", recordedAt);
        return row;
    }

    /** 解析 mapper 输出 '||' 字段 + ';;' 多样的拼接串为样例列表。 */
    static List<Map<String, Object>> parseSampleReasons(Object raw) {
        if (raw == null) return List.of();
        String s = raw.toString();
        if (s.isEmpty()) return List.of();
        String[] items = s.split(";;");
        List<Map<String, Object>> out = new ArrayList<>(items.length);
        for (String item : items) {
            if (item == null || item.isEmpty()) continue;
            String[] parts = item.split("\\|\\|", 4);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reviewerEmpNo", parts.length > 0 ? parts[0] : "");
            m.put("reviewerEmpName", parts.length > 1 ? parts[1] : "");
            m.put("createdAt", parts.length > 2 ? parts[2] : "");
            m.put("reason", parts.length > 3 ? parts[3] : "");
            out.add(m);
        }
        return out;
    }

    /** 四舍五入到 N 位小数；null 输入直接返回 null。 */
    static Double roundHalfUp(Double value, int decimals) {
        if (value == null) return null;
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    /** 与 CpsMemoryAdminService 对齐：钳制 pageSize 在 [1, MAX_PAGE_SIZE]，默认 DEFAULT_PAGE_SIZE。 */
    static int[] normalizePage(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;
        return new int[]{offset, safeSize};
    }

    private static String trim(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
