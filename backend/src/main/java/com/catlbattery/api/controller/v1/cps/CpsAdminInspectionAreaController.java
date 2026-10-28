package com.catlbattery.api.controller.v1.cps;

import com.catlbattery.domain.entity.cps.CpsInspectionArea;
import com.catlbattery.domain.entity.cps.dto.CpsEnabledRequest;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionAreaRequest;
import com.catlbattery.app.service.cps.CpsInspectionAreaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * 管理端 - 巡检区域 CRUD（Phase C1，替代原辅房台账）。
 */
@RestController
@RequestMapping("/api/cps/admin/inspection-areas")
public class CpsAdminInspectionAreaController {

    private final CpsInspectionAreaService areaService;

    public CpsAdminInspectionAreaController(CpsInspectionAreaService areaService) {
        this.areaService = areaService;
    }

    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(required = false) String typeCode,
                                    @RequestParam(required = false) String factory,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Boolean enabled,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        return areaService.page(typeCode, factory, keyword, enabled, page, pageSize);
    }

    @GetMapping("/{id}")
    public CpsInspectionArea get(@PathVariable Long id) {
        return areaService.getById(id);
    }

    /** 新建（body 无 id）或更新（query id） */
    @PostMapping
    public CpsInspectionArea save(@RequestParam(required = false) Long id,
                                  @RequestBody CpsInspectionAreaRequest request) {
        return areaService.save(id, request, resolveOperator(request.getOperatorEmpNo()));
    }

    @PatchMapping("/{id}/enabled")
    public void setEnabled(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        areaService.setEnabled(id, request.getEnabled(), resolveOperator(request.getEmpNo()));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        areaService.delete(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return resp;
    }

    private String resolveOperator(String empNo) {
        return empNo == null || empNo.trim().isEmpty() ? "DEV_EMP" : empNo.trim();
    }

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
        return body;
    }
}
