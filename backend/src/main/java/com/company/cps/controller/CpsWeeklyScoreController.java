package com.company.cps.controller;

import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.dto.CpsWeeklyScoreRecomputeRequest;
import com.company.cps.dto.CpsWeeklyScoreResponse;
import com.company.cps.service.CpsWeeklyScoreService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * B4 周评分排名（PRD §24）：
 * - GET /api/cps/admin/scores/weekly：分页 + 区域过滤 + 可选展开明细；
 * - POST /api/cps/admin/scores/weekly/recompute：手动触发上周重算。
 *
 * 鉴权由部署层企业 SSO/网关统一注入（沿用波次 2 管理端口径）。
 */
@RestController
@RequestMapping("/api/cps/admin/scores/weekly")
public class CpsWeeklyScoreController {

    private final CpsWeeklyScoreService service;

    public CpsWeeklyScoreController(CpsWeeklyScoreService service) {
        this.service = service;
    }

    @GetMapping
    public CpsAdminPageResponse<CpsWeeklyScoreResponse> list(
            @RequestParam("weekStartDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(value = "regionSupervisorId", required = false) Long regionSupervisorId,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "50") int pageSize,
            @RequestParam(value = "includeLines", required = false, defaultValue = "false") boolean includeLines) {
        return service.list(weekStartDate, regionSupervisorId, page, pageSize, includeLines);
    }

    @PostMapping("/recompute")
    public Map<String, Object> recompute(@RequestBody(required = false) CpsWeeklyScoreRecomputeRequest request) {
        return service.recompute(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }
}
