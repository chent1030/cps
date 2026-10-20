package com.company.cps.controller;

import com.company.cps.dto.CpsInspectionTypeDto;
import com.company.cps.service.CpsInspectionTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端（Android / 第三方）只读接口：只返回 enabled=1 的类型 + 完整 schema。
 * 路径前缀与 admin 端不同，便于后续做无鉴权/低鉴权 CDN 加速。
 */
@RestController
@RequestMapping("/api/cps/inspection-types")
public class CpsInspectionTypeQueryController {

    @Autowired
    private CpsInspectionTypeService service;

    @GetMapping
    public List<CpsInspectionTypeDto> listEnabled() {
        return service.listEnabled();
    }

    @GetMapping("/{code}")
    public CpsInspectionTypeDto getByCode(@PathVariable String code) {
        return service.findByCodeOrThrow(code);
    }

    /** Phase D2：类型的点检项明细（新建问题填写页按配置渲染；仅不分区域类型） */
    @GetMapping("/{code}/check-items")
    public java.util.List<com.company.cps.domain.CpsCheckItem> checkItems(@PathVariable String code) {
        return service.listCheckItemsByType(code);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "BAD_REQUEST");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
