package com.company.cps.controller;

import com.company.cps.dto.CpsPageResponse;
import com.company.cps.service.CpsCoverageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * FR-10 历史/覆盖分析管理端聚合查询：
 * - /api/cps/admin/coverage/frequency            频率分布
 * - /api/cps/admin/coverage/region-supervisor    区域处理人监控
 * - /api/cps/admin/coverage/recurrence           复发分析
 * - /api/cps/admin/coverage/gaps                 覆盖缺口
 * - /api/cps/admin/coverage/effect               效果评估四 metric（FR-12）
 *
 * 仅 cps_admin 角色使用；鉴权由部署层 SSO 注入（沿用波次 1/9 admin 弱鉴权注释）。
 */
@RestController
@RequestMapping("/api/cps/admin/coverage")
public class CpsCoverageController {

    private final CpsCoverageService service;

    public CpsCoverageController(CpsCoverageService service) {
        this.service = service;
    }

    @GetMapping("/frequency")
    public CpsPageResponse<Map<String, Object>> frequency(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Long categoryL1Id,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "200") Integer size) {
        return service.frequencyGroupBy(factory, area, categoryL1Id, startTime, endTime, page, size);
    }

    @GetMapping("/region-supervisor")
    public CpsPageResponse<Map<String, Object>> regionSupervisor(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Long categoryL1Id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer overdueDays,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "200") Integer size) {
        return service.regionSupervisorGroupBy(factory, area, categoryL1Id, status, overdueDays, page, size);
    }

    @GetMapping("/recurrence")
    public CpsPageResponse<Map<String, Object>> recurrence(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Long categoryL1Id,
            @RequestParam(required = false) Integer threshold,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "200") Integer size) {
        return service.recurrenceGroupBy(startTime, endTime, categoryL1Id, threshold, page, size);
    }

    @GetMapping("/gaps")
    public CpsPageResponse<Map<String, Object>> gaps(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String storageRoomType,
            @RequestParam(required = false) Integer gapDays,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "200") Integer size) {
        return service.coverageGaps(factory, area, storageRoomType, gapDays, page, size);
    }

    /**
     * FR-12 效果评估指标查询：四 metric（ai_pass_rate / human_override_rate / avg_close_duration_hours / recurrence_rate_30d）。
     * periodStart/periodEnd 默认 30 天窗口；recurrence_rate_30d 实际按 90 天窗口计算（与 Wave 10 recurrenceGroupBy 对齐）。
     */
    @GetMapping("/effect")
    public CpsPageResponse<Map<String, Object>> effect(
            @RequestParam(required = false) String periodStart,
            @RequestParam(required = false) String periodEnd,
            @RequestParam(required = false) String metricKey,
            @RequestParam(required = false) String scopeKey) {
        return service.effect(periodStart, periodEnd, metricKey, scopeKey);
    }
}
