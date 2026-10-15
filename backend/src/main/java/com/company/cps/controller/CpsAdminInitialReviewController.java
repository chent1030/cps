package com.company.cps.controller;

import com.company.cps.dto.CpsAdminInitialReviewConfigRequest;
import com.company.cps.dto.CpsAdminRetriggerRequest;
import com.company.cps.service.CpsInitialReviewAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A4 初审触发管理（PRD §28.4/§29.3，D-21）。
 * 鉴权由部署层的企业 SSO 或网关统一注入（沿用波次1管理端口径）。
 */
@RestController
@RequestMapping("/api/cps/admin/initial-review")
public class CpsAdminInitialReviewController {

    private final CpsInitialReviewAdminService adminService;

    public CpsAdminInitialReviewController(CpsInitialReviewAdminService adminService) {
        this.adminService = adminService;
    }

    /** 触发配置：自动触发开关 + 技术重试策略 + 接管阈值秒（NULL=回退应用配置）。 */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return adminService.getConfig();
    }

    /**
     * 更新触发配置（PUT 全量替换）：autoTrigger/maxRetry/backoff 必填；
     * maxRetry 0..5、backoff 0..60000ms、timeout 30..86400s 或 NULL。
     */
    @PutMapping("/config")
    public Map<String, Object> updateConfig(
            @RequestBody CpsAdminInitialReviewConfigRequest request
    ) {
        return adminService.updateConfig(request);
    }

    /** 触发记录分页：可选 status/issueId 过滤，含问题状态/AI 结果/裁决关联视图。 */
    @GetMapping("/tasks")
    public Map<String, Object> tasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long issueId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return adminService.listTasks(status, issueId, page, pageSize);
    }

    /** 触发任务详情：任务 + AI 结果 + 逐项意见 + 事件流水 + 既有裁决。 */
    @GetMapping("/tasks/{id}")
    public Map<String, Object> taskDetail(
            @PathVariable Long id
    ) {
        return adminService.taskDetail(id);
    }

    /** 手动重触发失败任务（仅 FAILED/PENDING_DISPATCH 且该版本未裁决；reason 必填）。 */
    @PostMapping("/tasks/{id}/retrigger")
    public Map<String, Object> retrigger(
            @PathVariable Long id,
            @RequestBody CpsAdminRetriggerRequest request
    ) {
        return adminService.retrigger(id, request.getOperatorEmpNo(), request.getReason());
    }
}
