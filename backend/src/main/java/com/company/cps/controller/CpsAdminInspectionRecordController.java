package com.company.cps.controller;

import com.company.cps.service.CpsInspectionRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端 - 统一巡检记录查询（Phase C1，替代原点检记录页数据源）。
 */
@RestController
@RequestMapping("/api/cps/admin/inspection-records")
public class CpsAdminInspectionRecordController {

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

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return recordService.detail(id);
    }
}
