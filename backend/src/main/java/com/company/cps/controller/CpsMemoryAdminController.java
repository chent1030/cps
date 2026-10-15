package com.company.cps.controller;

import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.dto.CpsAdjudicationMemoryItem;
import com.company.cps.dto.CpsInitialReviewEventMemoryItem;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.service.CpsCoverageService;
import com.company.cps.service.CpsMemoryAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * I 线记忆体系消费端（FR-09 历史裁决/事件查询 + FR-11 procedural 调度记忆消费端）：
 * - /api/cps/admin/memory/adjudications - 裁决
 * - /api/cps/admin/memory/events        - AI 初审事件
 * - /api/cps/admin/memory/issues       - 问题上下文
 * - /api/cps/admin/memory/dispatcher   - 调度记忆聚合（FR-11）
 * 仅 cps_admin 角色使用；鉴权由部署层 SSO 注入（沿用波次 1/9 admin 弱鉴权注释）。
 */
@RestController
@RequestMapping("/api/cps/admin/memory")
public class CpsMemoryAdminController {

    private final CpsMemoryAdminService service;
    private final CpsCoverageService coverageService;

    public CpsMemoryAdminController(CpsMemoryAdminService service,
                                    CpsCoverageService coverageService) {
        this.service = service;
        this.coverageService = coverageService;
    }

    @GetMapping("/adjudications")
    public CpsPageResponse<CpsAdjudicationMemoryItem> listAdjudications(
            @RequestParam(required = false) Long issueId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String aiRelation,
            @RequestParam(required = false) String reviewerEmpNo,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Long categoryL1Id,
            @RequestParam(required = false) Long categoryL2Id,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "100") Integer size) {
        return service.listAdjudications(
                issueId, decision, aiRelation, reviewerEmpNo,
                factory, area, categoryL1Id, categoryL2Id,
                startTime, endTime, page, size);
    }

    @GetMapping("/events")
    public CpsPageResponse<CpsInitialReviewEventMemoryItem> listEvents(
            @RequestParam(required = false) Long issueId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Long categoryL1Id,
            @RequestParam(required = false) Long categoryL2Id,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "100") Integer size) {
        return service.listEvents(
                issueId, eventType, taskId,
                factory, area, categoryL1Id, categoryL2Id,
                startTime, endTime, page, size);
    }

    @GetMapping("/issues")
    public CpsPageResponse<CpsIssueListItemResponse> listIssues(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) Long categoryL1Id,
            @RequestParam(required = false) Long categoryL2Id,
            @RequestParam(required = false) CpsIssueStatus status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "100") Integer size) {
        return service.listIssues(
                factory, categoryL1Id, categoryL2Id,
                status, startTime, endTime, page, size);
    }

    /**
     * FR-11 procedural 调度记忆消费端：按 (category_l1_id, area, decision, ai_relation) 维度聚合历史人工裁决。
     * 每聚合组附带最近 5 条样例裁决（reviewer/createdAt/reason 200 字截断）。
     * agent 在做"转派/分类/裁决"等调度动作前主动查询。
     */
    @GetMapping("/dispatcher")
    public CpsPageResponse<Map<String, Object>> dispatcher(
            @RequestParam(required = false) Long categoryL1Id,
            @RequestParam(required = false) Long categoryL2Id,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String aiRelation,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "200") Integer size) {
        return coverageService.dispatcherMemoryGroupBy(
                categoryL1Id, categoryL2Id,
                factory, area, decision, aiRelation,
                startTime, endTime, page, size);
    }
}