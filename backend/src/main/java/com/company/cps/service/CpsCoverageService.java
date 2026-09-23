package com.company.cps.service;

import com.company.cps.dto.CpsPageResponse;
import com.company.cps.mapper.CpsCoverageMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * FR-10 历史/覆盖分析聚合查询（波次10）：
 * 复用 cps_issue + cps_problem_category + cps_room + cps_room_check_record/cps_inspection_item 的 JOIN，
 * 输出四类覆盖维度聚合行：频率分布 / 区域处理人监控 / 复发分析 / 覆盖缺口。
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

    private final CpsCoverageMapper coverageMapper;

    public CpsCoverageService(CpsCoverageMapper coverageMapper) {
        this.coverageMapper = coverageMapper;
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
