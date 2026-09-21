package com.company.cps.controller;

import com.company.cps.dto.CpsAdminOverviewResponse;
import com.company.cps.dto.CpsIssueAdminPageResponse;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.service.CpsAdminIssueService;
import com.company.cps.support.CpsExcelWriter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 * 管理端聚合查询入口。鉴权由部署层的企业 SSO 或网关统一注入。
 */
@RestController
@RequestMapping("/api/cps/admin")
public class CpsAdminIssueController {

    private final CpsAdminIssueService adminIssueService;

    public CpsAdminIssueController(CpsAdminIssueService adminIssueService) {
        this.adminIssueService = adminIssueService;
    }

    @GetMapping("/overview")
    public CpsAdminOverviewResponse overview() {
        return adminIssueService.overview();
    }

    @GetMapping("/issues")
    public CpsIssueAdminPageResponse issues(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) String currentHandler,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return adminIssueService.listIssues(status, factory, area, line, process, currentHandler, createdFrom, createdTo, keyword, page, pageSize);
    }

    @GetMapping("/issues/export")
    public void exportIssues(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) String currentHandler,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String keyword,
            HttpServletResponse response
    ) throws IOException {
        List<CpsIssueListItemResponse> records = adminIssueService.exportIssues(status, factory, area, line, process, currentHandler, createdFrom, createdTo, keyword);
        List<String[]> rows = new ArrayList<>();
        for (CpsIssueListItemResponse item : records) {
            rows.add(new String[] {
                    item.getStatus() == null ? "" : item.getStatus().name(),
                    item.getFactory(),
                    item.getArea(),
                    item.getLine(),
                    item.getProcess(),
                    item.getDescription(),
                    item.getCurrentHandlerEmpNo(),
                    item.getCurrentHandlerEmpName(),
                    item.getSubmitTime() == null ? "" : item.getSubmitTime().toString(),
                    Boolean.TRUE.equals(item.getOverdue()) ? "是" : "否"
            });
        }
        CpsExcelWriter.write(response, "cps-issues.xlsx", "问题管理",
                new String[] {"状态", "工厂", "区域", "拉线", "工序", "问题描述", "处理人工号", "处理人姓名", "提交时间", "是否超期"},
                rows);
    }
}
