package com.company.cps.controller;

import com.company.cps.service.CpsWeeklyReportDataService;
import com.company.cps.service.CpsWeeklyReportDataService.Window;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B7 周报数据源读端点（PRD §21.1/§21.3；后端架构 §2.1）。
 *
 * <p>面向内部 Python 报告 Agent 的取数接口：
 * <ul>
 *   <li>window 参数可选；缺省按 §21.1.6 计算"上一完整自然周"窗口（东八区）</li>
 *   <li>视图 weekly_report_data_* 由 V20260927 提供，service 仅做窗口投影</li>
 *   <li>本端点不做权限校验——生产环境需在网关/反向代理层做内网 CIDR 校验
 *       （沿用 cps.callback.trusted-ips 思路）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/cps/weekly-report-data")
public class CpsWeeklyReportDataController {

    private final CpsWeeklyReportDataService dataService;

    public CpsWeeklyReportDataController(CpsWeeklyReportDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/window/current")
    public Map<String, Object> currentWindow() {
        Window window = dataService.currentWeekWindow();
        return windowBody(window);
    }

    @GetMapping("/issues")
    public Map<String, Object> issues(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodEnd) {
        Window window = resolveWindow(periodStart, periodEnd);
        return body(window, "issue_summary", dataService.getIssueSummary(factory, window.getPeriodStart(), window.getPeriodEnd()));
    }

    @GetMapping("/rectifications")
    public Map<String, Object> rectifications(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodEnd) {
        Window window = resolveWindow(periodStart, periodEnd);
        return body(window, "rectification_summary",
                dataService.getRectificationSummary(window.getPeriodStart(), window.getPeriodEnd()));
    }

    @GetMapping("/initial-reviews")
    public Map<String, Object> initialReviews(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodEnd) {
        Window window = resolveWindow(periodStart, periodEnd);
        return body(window, "initial_review_summary",
                dataService.getInitialReviewSummary(window.getPeriodStart(), window.getPeriodEnd()));
    }

    @GetMapping("/check-items")
    public Map<String, Object> checkItems() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", dataService.getCheckItemSnapshot());
        body.put("count", dataService.getCheckItemSnapshot().size());
        return body;
    }

    @GetMapping("/inventory/low-stock")
    public Map<String, Object> inventoryLowStock() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", dataService.getInventoryLowStock());
        body.put("count", dataService.getInventoryLowStock().size());
        return body;
    }

    private Map<String, Object> body(Window window, String key, List<Map<String, Object>> data) {
        Map<String, Object> body = windowBody(window);
        body.put(key, data);
        body.put("count", data.size());
        return body;
    }

    private static Map<String, Object> windowBody(Window window) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period_start", window.getPeriodStart());
        body.put("period_end", window.getPeriodEnd());
        body.put("window_semantics", "left-closed right-open [period_start, period_end)");
        return body;
    }

    private Window resolveWindow(LocalDateTime periodStart, LocalDateTime periodEnd) {
        if (periodStart == null && periodEnd == null) return dataService.currentWeekWindow();
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("Both periodStart and periodEnd are required when one is provided");
        }
        return new Window(periodStart, periodEnd);
    }
}
