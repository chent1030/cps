package com.company.cps.controller;

import com.company.cps.service.CpsInspectionRecordService;
import com.company.cps.support.CpsExcelWriter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 管理端 - 统一巡检记录查询（Phase C1，替代原点检记录页数据源）。
 */
@RestController
@RequestMapping("/api/cps/admin/inspection-records")
public class CpsAdminInspectionRecordController {

    private static final String[] EXPORT_HEADERS = {
            "记录ID", "巡检类型", "区域", "巡检人", "状态", "提交时间", "得分",
            "AI初审结论", "AI初审意见", "复核人", "复核时间", "复核意见",
            "整改人", "整改提交时间", "整改原因", "短期措施", "长期措施", "整改照片"
    };

    private final CpsInspectionRecordService recordService;

    public CpsAdminInspectionRecordController(CpsInspectionRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(required = false) String typeCode,
                                    @RequestParam(required = false) Long areaId,
                                    @RequestParam(required = false) String recordStatus,
                                    @RequestParam(required = false) String checkEmpNo,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        return recordService.page(typeCode, areaId, recordStatus, checkEmpNo, keyword, page, pageSize);
    }

    /** 巡检记录导出（非 CPS 巡检）：筛选条件与列表页一致。 */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String typeCode,
                       @RequestParam(required = false) Long areaId,
                       @RequestParam(required = false) String recordStatus,
                       @RequestParam(required = false) String checkEmpNo,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        List<String[]> rows = recordService.exportRows(typeCode, areaId, recordStatus, checkEmpNo, keyword);
        CpsExcelWriter.write(response, "cps-inspection-records.xlsx", "巡检记录", EXPORT_HEADERS, rows);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return recordService.detail(id);
    }
}
