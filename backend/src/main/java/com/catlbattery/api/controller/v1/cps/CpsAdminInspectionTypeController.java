package com.catlbattery.api.controller.v1.cps;

import com.catlbattery.domain.entity.cps.dto.CpsInspectionTypeDto;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionTypeRequest;
import com.catlbattery.app.service.cps.CpsInspectionTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端 - 巡检问题类型 CRUD。接入 admin 鉴权链（CpsAdminAuthInterceptor），
 * 任何 admin 账号都能管理（V1 暂不细分权限）。
 */
@RestController
@RequestMapping("/api/cps/admin/inspection-types")
public class CpsAdminInspectionTypeController {

    @Autowired
    private CpsInspectionTypeService service;

    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Integer enabled,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        return service.page(keyword, enabled, page, pageSize);
    }

    @GetMapping("/{id}")
    public CpsInspectionTypeDto get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public CpsInspectionTypeDto create(@RequestBody CpsInspectionTypeRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    public CpsInspectionTypeDto update(@PathVariable Long id, @RequestBody CpsInspectionTypeRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return java.util.Collections.singletonMap("ok", true);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("error", "BAD_REQUEST");
        body.put("message", e.getMessage());
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(body);
    }
}
