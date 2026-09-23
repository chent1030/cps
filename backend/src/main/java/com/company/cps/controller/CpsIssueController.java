package com.company.cps.controller;

import com.company.cps.dto.CpsIssueActionRequest;
import com.company.cps.dto.CpsIssueActionResponse;
import com.company.cps.dto.CpsIssueCreateRequest;
import com.company.cps.dto.CpsIssueCreateResponse;
import com.company.cps.dto.CpsIssueDetailResponse;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.dto.CpsInitialReviewTakeOverRequest;
import com.company.cps.dto.CpsReviewerReassignRequest;
import com.company.cps.dto.CpsReviewAdjudicateRequest;
import com.company.cps.dto.CpsReviewAdjudicationResponse;
import com.company.cps.service.CpsInitialReviewService;
import com.company.cps.service.CpsIssueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cps/issues")
public class CpsIssueController {

    private final CpsIssueService issueService;
    private final CpsInitialReviewService initialReviewService;

    public CpsIssueController(CpsIssueService issueService, CpsInitialReviewService initialReviewService) {
        this.issueService = issueService;
        this.initialReviewService = initialReviewService;
    }

    /**
     * 创建巡检问题，绑定问题照片并进入待反馈节点。
     */
    @PostMapping
    public CpsIssueCreateResponse create(
            @RequestBody CpsIssueCreateRequest request
    ) {
        Long issueId = issueService.createIssue(request, resolveCurrentEmpNo(request.getEmpNo()));
        return new CpsIssueCreateResponse(issueId);
    }

    /**
     * 查询当前用户的问题列表，支持待办、我创建的、与我相关、已关闭等页签。
     */
    @GetMapping
    public List<CpsIssueListItemResponse> list(
            @RequestParam(defaultValue = "todo") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam String empNo
    ) {
        return issueService.list(tab, page, pageSize, resolveCurrentEmpNo(empNo));
    }

    /**
     * 查询问题详情，包括问题主信息、照片、AI建议、流程记录和当前可操作动作。
     */
    @GetMapping("/{id}")
    public CpsIssueDetailResponse detail(
            @PathVariable Long id,
            @RequestParam String empNo
    ) {
        return issueService.getDetail(id, resolveCurrentEmpNo(empNo));
    }

    /**
     * 执行问题流程动作，如反馈分派、整改、上传凭证、审核关闭、驳回或转派。
     */
    @PostMapping("/{id}/actions")
    public CpsIssueActionResponse action(
            @PathVariable Long id,
            @RequestBody CpsIssueActionRequest request
    ) {
        return issueService.executeAction(id, request, resolveCurrentEmpNo(request.getEmpNo()));
    }

    /**
     * A2 管理员改配审核员（PRD §30.2，AC-25）：未完成审核单转新审核员，原审核员失权；
     * 不重置 AI 初审计时；reason 必填留痕；已完成审核单不可改配。
     */
    @PostMapping("/{id}/reassign-reviewer")
    public CpsIssueActionResponse reassignReviewer(
            @PathVariable Long id,
            @RequestBody CpsReviewerReassignRequest request
    ) {
        return issueService.reassignReviewer(
                id,
                request.getReviewerEmpNo(),
                resolveCurrentEmpNo(request.getOperatorEmpNo()),
                request.getReason()
        );
    }

    /**
     * A3 审核员初审视图（PRD §28.2/§29）：AI 初审三态（running/failed/timeout_open 等）+
     * 可接管性与剩余秒数 + AI 结果与逐项意见 + 提交快照 + 既有裁决 + 事件流水。
     */
    @GetMapping("/{id}/initial-review")
    public java.util.Map<String, Object> initialReviewView(
            @PathVariable Long id
    ) {
        return initialReviewService.reviewerView(id);
    }

    /**
     * A3 超时接管（PRD §28.2/§29，AC-27）：RUNNING 满 10 分钟（或 FAILED）后方可接管，
     * RUNNING 未满 10 分钟拒绝；接管必须注明原因；接管后开放人工裁决。
     */
    @PostMapping("/{id}/initial-review/take-over")
    public java.util.Map<String, Object> takeOverInitialReview(
            @PathVariable Long id,
            @RequestBody CpsInitialReviewTakeOverRequest request
    ) {
        return initialReviewService.takeOverForIssue(
                id,
                resolveCurrentEmpNo(request.getEmpNo()),
                request.getReason()
        );
    }

    /**
     * A3 审核裁决（PRD §28.3，AC-16）：APPROVE=通过关单、REJECT=退回整改人员；
     * 理由必填；同 (issue, version) 重复裁决幂等返回 duplicated=true。
     */
    @PostMapping("/{id}/adjudicate")
    public CpsReviewAdjudicationResponse adjudicate(
            @PathVariable Long id,
            @RequestBody CpsReviewAdjudicateRequest request
    ) {
        return issueService.adjudicate(
                id,
                request.getDecision(),
                request.getReason(),
                resolveCurrentEmpNo(request.getEmpNo())
        );
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }
}
