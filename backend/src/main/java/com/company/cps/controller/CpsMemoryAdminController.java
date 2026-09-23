package com.company.cps.controller;

import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.dto.CpsAdjudicationMemoryItem;
import com.company.cps.dto.CpsInitialReviewEventMemoryItem;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.service.CpsMemoryAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * I 线记忆体系消费端（FR-09 历史裁决/事件查询）：
 * - /api/cps/admin/memory/adjudications - 裁决
 * - /api/cps/admin/memory/events        - AI 初审事件
 * - /api/cps/admin/memory/issues       - 问题上下文
 * 仅 cps_admin 角色使用；鉴权由部署层 SSO 注入（沿用波次 1 admin 弱鉴权注释）。
 */
@RestController
@RequestMapping("/api/cps/admin/memory")
public class CpsMemoryAdminController {

    private final CpsMemoryAdminService service;

    public CpsMemoryAdminController(CpsMemoryAdminService service) {
        this.service = service;
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
}