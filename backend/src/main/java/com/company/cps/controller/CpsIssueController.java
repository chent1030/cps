package com.company.cps.controller;

import com.company.cps.dto.CpsIssueActionRequest;
import com.company.cps.dto.CpsIssueActionResponse;
import com.company.cps.dto.CpsIssueCreateRequest;
import com.company.cps.dto.CpsIssueCreateResponse;
import com.company.cps.dto.CpsIssueDetailResponse;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.service.CpsIssueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cps/issues")
public class CpsIssueController {

    private final CpsIssueService issueService;

    public CpsIssueController(CpsIssueService issueService) {
        this.issueService = issueService;
    }

    /**
     * 创建巡检问题，绑定问题照片并进入待反馈节点。
     */
    @PostMapping
    public CpsIssueCreateResponse create(
            @RequestBody CpsIssueCreateRequest request,
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        Long issueId = issueService.createIssue(request, resolveCurrentEmpNo(empNo));
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
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        return issueService.list(tab, page, pageSize, resolveCurrentEmpNo(empNo));
    }

    /**
     * 查询问题详情，包括问题主信息、照片、AI建议、流程记录和当前可操作动作。
     */
    @GetMapping("/{id}")
    public CpsIssueDetailResponse detail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        return issueService.getDetail(id, resolveCurrentEmpNo(empNo));
    }

    /**
     * 执行问题流程动作，如反馈分派、整改、上传凭证、审核关闭、驳回或转派。
     */
    @PostMapping("/{id}/actions")
    public CpsIssueActionResponse action(
            @PathVariable Long id,
            @RequestBody CpsIssueActionRequest request,
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        return issueService.executeAction(id, request, resolveCurrentEmpNo(empNo));
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }
}
