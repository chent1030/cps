package com.catlbattery.api.controller.v1.cps;

import com.catlbattery.domain.entity.cps.dto.CpsAdminAuditLogDto;
import com.catlbattery.domain.entity.cps.dto.CpsAdminPageResponse;
import com.catlbattery.app.service.cps.CpsAdminAuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cps/admin/audit-logs")
public class CpsAdminAuditLogController {

    private final CpsAdminAuditLogService service;

    public CpsAdminAuditLogController(CpsAdminAuditLogService service) {
        this.service = service;
    }

    @GetMapping("/page")
    public CpsAdminPageResponse<CpsAdminAuditLogDto> page(
            @RequestParam(required = false) String empNo,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize
    ) {
        return service.page(empNo, action, status, keyword, page, pageSize);
    }
}
