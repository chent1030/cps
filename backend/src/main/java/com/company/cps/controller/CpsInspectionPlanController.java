package com.company.cps.controller;

import com.company.cps.domain.CpsInspectionPlan;
import com.company.cps.domain.CpsInspectionPlanStatus;
import com.company.cps.dto.CpsInspectionPlanApproveRequest;
import com.company.cps.dto.CpsInspectionPlanRejectRequest;
import com.company.cps.dto.CpsInspectionPlanRequest;
import com.company.cps.dto.CpsInspectionPlanResponse;
import com.company.cps.service.CpsInspectionPlanService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * D1 申请草稿 + D2 审核界面（PRD §22.1/§22.2；AC-30）。
 *
 * <p>admin 弱鉴权沿用波次 2：operator 参数必填但仅做日志留痕，不做强制校验。
 * 生产前需补统一鉴权拦截。
 */
@RestController
@RequestMapping("/api/cps/inspection-plans")
public class CpsInspectionPlanController {

    private final CpsInspectionPlanService planService;

    public CpsInspectionPlanController(CpsInspectionPlanService planService) {
        this.planService = planService;
    }

    /** D1：申请草稿（调 C-07）。 */
    @PostMapping("/apply-draft")
    public CpsInspectionPlanResponse applyDraft(@RequestBody CpsInspectionPlanRequest request) {
        CpsInspectionPlan plan = planService.applyDraft(request);
        List<com.company.cps.domain.CpsInspectionPlanTask> tasks = planService.listTasks(plan.getId());
        return CpsInspectionPlanResponse.from(plan, tasks);
    }

    /** D2：审核列表（默认 PENDING_REVIEW）。 */
    @GetMapping
    public List<CpsInspectionPlanResponse> list(@RequestParam(required = false) CpsInspectionPlanStatus status) {
        List<CpsInspectionPlan> plans = planService.listByStatus(status);
        return plans.stream()
                .map(p -> CpsInspectionPlanResponse.from(p, planService.listTasks(p.getId())))
                .collect(Collectors.toList());
    }

    /** D2：详情（含任务）。 */
    @GetMapping("/{id}")
    public CpsInspectionPlanResponse detail(@PathVariable Long id) {
        CpsInspectionPlan plan = planService.getDetail(id);
        return CpsInspectionPlanResponse.from(plan, planService.listTasks(id));
    }

    /** D2：批准。返回 plan + 本次新增的任务列表（D3 建单引擎结果）。 */
    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(
            @PathVariable Long id,
            @RequestBody CpsInspectionPlanApproveRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo) {
        CpsInspectionPlanService.ApproveResult result = planService.approve(id, request);
        Map<String, Object> body = new HashMap<>();
        body.put("plan", CpsInspectionPlanResponse.from(result.getPlan(), planService.listTasks(id)));
        body.put("createdTasks", result.getCreatedTasks());
        body.put("operator", operatorEmpNo);
        return body;
    }

    /** D2：拒绝（必填 reason）。 */
    @PostMapping("/{id}/reject")
    public CpsInspectionPlanResponse reject(
            @PathVariable Long id,
            @RequestBody CpsInspectionPlanRejectRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo) {
        CpsInspectionPlan plan = planService.reject(id, request);
        return CpsInspectionPlanResponse.from(plan, planService.listTasks(id));
    }

    /** D4：建单记录状态聚合视图（计划→建单结果→任务状态；AC-06/30 可查/可追溯）。 */
    @GetMapping("/{id}/record-status")
    public com.company.cps.dto.CpsPlanRecordStatusResponse recordStatus(@PathVariable Long id) {
        return planService.recordStatus(id);
    }

    /** D4：补建（仅 APPROVED 可调；仅补 CREATE_FAILED/缺失类型，已成功项不重复创建，AC-30）。 */
    @PostMapping("/{id}/rebuild-tasks")
    public Map<String, Object> rebuildTasks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "admin") String operatorEmpNo) {
        List<com.company.cps.domain.CpsInspectionPlanTask> created = planService.rebuildTasks(id, operatorEmpNo);
        Map<String, Object> body = new HashMap<>();
        body.put("createdTasks", created);
        body.put("recordStatus", planService.recordStatus(id));
        body.put("operator", operatorEmpNo);
        return body;
    }

    /** 强制 LocalDateTime 解析为 ISO_LOCAL_DATETIME 格式（前端传入）。 */
    @SuppressWarnings("unused")
    private static class ParamBindHelper {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private java.time.LocalDateTime periodStart;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private java.time.LocalDateTime periodEnd;
    }
}
