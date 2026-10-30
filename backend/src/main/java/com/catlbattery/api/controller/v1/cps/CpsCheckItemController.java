package com.catlbattery.api.controller.v1.cps;

import com.catlbattery.domain.entity.cps.CpsCheckItem;
import com.catlbattery.domain.entity.cps.dto.CpsCheckItemRequest;
import com.catlbattery.domain.entity.cps.dto.CpsEnabledRequest;
import com.catlbattery.app.service.cps.CpsCheckItemService;
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

/** B2 辅房点检项配置维护（PRD §23.2，配置版本随更新自动递增）。 */
@RestController
@RequestMapping("/api/cps/admin/check-items")
public class CpsCheckItemController {

    private final CpsCheckItemService checkItemService;

    public CpsCheckItemController(CpsCheckItemService checkItemService) {
        this.checkItemService = checkItemService;
    }

    @GetMapping
    public List<CpsCheckItem> list(
            @RequestParam(required = false) String photoCategory,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean enabled
    ) {
        return checkItemService.list(photoCategory, status, enabled);
    }

    @GetMapping("/{id}")
    public CpsCheckItem detail(@PathVariable Long id) {
        return checkItemService.getDetail(id);
    }

    @PostMapping
    public CpsCheckItem save(
            @RequestParam(required = false) Long id,
            @RequestBody CpsCheckItemRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo
    ) {
        return checkItemService.save(id, request, operatorEmpNo);
    }

    @PatchMapping("/{id}/enabled")
    public void setEnabled(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        checkItemService.setEnabled(id, request.getEnabled(), request.getEmpNo());
    }

    /** B8：删除点检项（被区域/历史记录引用时返回 409，前端 errorMessage 可解析 message）。 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        checkItemService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
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
