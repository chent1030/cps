package com.company.cps.controller;

import com.company.cps.dto.CpsApprovalActionRequest;
import com.company.cps.dto.CpsApprovalInstanceDto;
import com.company.cps.dto.CpsApprovalStepDto;
import com.company.cps.dto.CpsInspectionPlanApproveRequest;
import com.company.cps.dto.CpsInspectionPlanRejectRequest;
import com.company.cps.service.CpsApprovalService;
import com.company.cps.service.CpsInspectionPlanService;
import com.company.cps.service.CpsIssueReviewApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

/**
 * 通用审批中心 API（Phase A2）。
 * <p>挂载在 {@code /api/cps/admin/approvals}，超管/限权人员都能调用 GET 列表，
 * 但只有超管能 approve/reject（实际限权交给前端 + 路由守卫）。
 */
@RestController
@RequestMapping("/api/cps/admin/approvals")
public class CpsApprovalController {

    private static final Logger log = LoggerFactory.getLogger(CpsApprovalController.class);

    private final CpsApprovalService approvalService;
    private final CpsInspectionPlanService inspectionPlanService;
    private final CpsIssueReviewApprovalService issueReviewApprovalService;

    public CpsApprovalController(CpsApprovalService approvalService,
                                  CpsInspectionPlanService inspectionPlanService,
                                  CpsIssueReviewApprovalService issueReviewApprovalService) {
        this.approvalService = approvalService;
        this.inspectionPlanService = inspectionPlanService;
        this.issueReviewApprovalService = issueReviewApprovalService;
    }

    /** 中心面板默认只看待审批（status=PENDING） */
    @GetMapping("/pending")
    public Map<String, Object> listPending(@RequestParam(required = false) String bizType,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("records", approvalService.listPending(bizType, keyword, page, pageSize));
        resp.put("total", approvalService.countPending(bizType, keyword));
        resp.put("page", page);
        resp.put("pageSize", pageSize);
        return resp;
    }

    /** 全量（含已审/已驳回），可按 status 过滤 */
    @GetMapping("/page")
    public Map<String, Object> listAll(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String bizType,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("records", approvalService.listAll(status, bizType, keyword, page, pageSize));
        resp.put("total", approvalService.countAll(status, bizType, keyword));
        resp.put("page", page);
        resp.put("pageSize", pageSize);
        return resp;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getDetail(@PathVariable Long id) {
        CpsApprovalInstanceDto dto = approvalService.getDetail(id);
        List<CpsApprovalStepDto> steps = approvalService.listSteps(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("instance", dto);
        resp.put("steps", steps);
        return resp;
    }

    /**
     * 补建历史待审核问题单的审批实例（审核搬到 web 审批中心上线前的存量数据）。
     * 幂等：已有实例的问题单不会重复建。
     */
    @PostMapping("/issue-rectify/backfill")
    public Map<String, Object> backfillIssueRectify(@RequestParam(defaultValue = "50") int limit) {
        int ensured = issueReviewApprovalService.backfillPendingReviews(limit);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ensured", ensured);
        return resp;
    }

    /**
     * 统一动作入口。前端根据 instance 状态决定按钮：approve / reject / withdraw。
     * action=SUBMIT 时不通过此接口（业务方自己 upsert）。
     * <p>支持双向同步：先在 cps_approval_instance 上写状态机，再回调到业务方
     * （{@code INSPECTION_PLAN} 巡检计划审批、{@code ISSUE_RECTIFY} 问题整改审核）。
     */
    @PostMapping("/{id}/action")
    public CpsApprovalInstanceDto act(@PathVariable Long id, @RequestBody CpsApprovalActionRequest request) {
        if (request == null || request.getAction() == null) {
            throw new IllegalArgumentException("action is required");
        }
        CpsApprovalInstanceDto dto;
        switch (request.getAction()) {
            case APPROVE:
                // 先在审批中心写状态机（已包含 SUBMIT/APPROVE 步骤），再回调业务方
                dto = approvalService.approve(id, request.getOperatorEmpNo(), request.getOperatorEmpName(), request.getComment());
                syncPlanApprove(dto, request);
                syncIssueDecision(dto, true, request);
                break;
            case REJECT:
                dto = approvalService.reject(id, request.getOperatorEmpNo(), request.getOperatorEmpName(), request.getComment());
                syncPlanReject(dto, request);
                syncIssueDecision(dto, false, request);
                break;
            case WITHDRAW:
                dto = approvalService.withdraw(id, request.getOperatorEmpNo(), request.getOperatorEmpName(), request.getComment());
                syncPlanWithdraw(dto);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action: " + request.getAction());
        }
        return dto;
    }

    /**
     * 批准时同步业务方：仅当 bizType=INSPECTION_PLAN 且 plan 仍处 PENDING_REVIEW 时调用 plan.approve。
     * 若业务方此前已自行审批过，这里 catch 异常后继续（审批中心仍记录）。
     */
    private void syncPlanApprove(CpsApprovalInstanceDto instance, CpsApprovalActionRequest request) {
        if (!"INSPECTION_PLAN".equals(instance.getBizType())) return;
        try {
            CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
            req.setApprover(request.getOperatorEmpNo());
            req.setApproverName(request.getOperatorEmpName());
            req.setComment(request.getComment());
            inspectionPlanService.approve(instance.getBizId(), req);
        } catch (Exception ignore) {
            // 业务方已审/已撤 → 中心侧的状态机仍生效；忽略差异
        }
    }

    private void syncPlanReject(CpsApprovalInstanceDto instance, CpsApprovalActionRequest request) {
        if (!"INSPECTION_PLAN".equals(instance.getBizType())) return;
        try {
            CpsInspectionPlanRejectRequest req = new CpsInspectionPlanRejectRequest();
            req.setApprover(request.getOperatorEmpNo());
            req.setApproverName(request.getOperatorEmpName());
            req.setReason(request.getComment());
            inspectionPlanService.reject(instance.getBizId(), req);
        } catch (Exception ignore) {
            // 同上
        }
    }

    /** 撤回时反向把 plan 重开为 PENDING_REVIEW（如有重提/编辑需求，调用方在业务页面再次 apply-draft） */
    private void syncPlanWithdraw(CpsApprovalInstanceDto instance) {
        if (!"INSPECTION_PLAN".equals(instance.getBizType())) return;
        // 暂不自动改 plan 状态：plan 一旦 APPROVED/REJECTED 就不让再改；
        // 撤回场景的常见做法是「业务方重新提交」，由其 apply-draft 重新 ensureInstance。
    }

    /**
     * 批准/驳回时同步问题单：仅当 bizType=ISSUE_RECTIFY 时驱动问题流程
     * （APPROVE → REVIEW_CLOSE 关单；REJECT → REVIEW_REJECT 退回整改人）。
     * 业务侧失败只记日志，不回滚审批中心的审批留痕。
     */
    private void syncIssueDecision(CpsApprovalInstanceDto instance, boolean approved,
                                   CpsApprovalActionRequest request) {
        if (!CpsIssueReviewApprovalService.BIZ_TYPE.equals(instance.getBizType())) return;
        try {
            issueReviewApprovalService.handleDecision(instance.getBizId(), approved, request.getComment(),
                    request.getOperatorEmpNo(), request.getOperatorEmpName());
        } catch (Exception exception) {
            log.error("Issue decision sync failed: approvalId={} submissionId={} approved={} reason={}",
                    instance.getId(), instance.getBizId(), approved, exception.getMessage(), exception);
        }
    }

    // ============================================================
    // 异常处理：参数错 → 400；状态错 → 409
    // ============================================================
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CONFLICT", e.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("status", code);
        return body;
    }
}
