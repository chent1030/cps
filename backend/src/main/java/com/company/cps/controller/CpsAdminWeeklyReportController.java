package com.company.cps.controller;

import com.company.cps.domain.CpsWeeklyReportRun;
import com.company.cps.service.CpsAgentFrameworkClient;
import com.company.cps.service.CpsWeeklyReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C5/C8 管理端周报代理（PRD §21.3；AC-04/05）。
 *
 * <p>查询列表 + 受控下载转发 Python C-05；Java 仅做参数透传 + 响应归一化。
 * admin 弱鉴权沿用波次 2（operatorEmpNo 仅日志留痕），生产前需补统一鉴权拦截。
 */
@RestController
@RequestMapping("/api/cps/admin/weekly-reports")
public class CpsAdminWeeklyReportController {

    private final CpsWeeklyReportService weeklyReportService;

    public CpsAdminWeeklyReportController(CpsWeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    @GetMapping
    public List<CpsWeeklyReportRun> list(
            @RequestParam(required = false) String inspectionType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime periodStart,
            @RequestParam(required = false) LocalDateTime periodEnd,
            @RequestParam(defaultValue = "admin") String operatorEmpNo) {
        return weeklyReportService.listRuns(inspectionType, status, periodStart, periodEnd);
    }

    @GetMapping("/{runId}/download")
    public void download(@PathVariable String runId,
                         @RequestParam(defaultValue = "admin") String operatorEmpNo,
                         HttpServletResponse response) throws IOException {
        CpsAgentFrameworkClient.WeeklyReportFile file = weeklyReportService.downloadFile(runId);
        if (file == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\":\"weekly report file not found or agent runtime disabled\"}");
            return;
        }
        response.setContentType(file.getContentType());
        if (file.getContentDisposition() != null) {
            response.setHeader("Content-Disposition", file.getContentDisposition());
        } else {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + runId + "\"");
        }
        response.setContentLength(file.getContent().length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(file.getContent());
        }
    }
}
