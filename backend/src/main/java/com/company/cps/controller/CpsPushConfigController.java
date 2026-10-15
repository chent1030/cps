package com.company.cps.controller;

import com.company.cps.dto.CpsPushConfigRequest;
import com.company.cps.service.CpsPushConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * F 线推送渠道配置（D-22）：
 * - GET /api/cps/admin/push-config：读取单行 GLOBAL；缺失/禁用 → configured=false（推送未配置）；
 * - PUT /api/cps/admin/push-config：upsert；enabled=true 时 endpoint 必填（否则 400）。
 * 鉴权由部署层企业 SSO/网关统一注入（沿用波次 2 管理端口径）。
 */
@RestController
@RequestMapping("/api/cps/admin/push-config")
public class CpsPushConfigController {

    private final CpsPushConfigService service;

    public CpsPushConfigController(CpsPushConfigService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> getConfig() {
        return service.getConfig();
    }

    @PutMapping
    public Map<String, Object> updateConfig(@RequestBody CpsPushConfigRequest request) {
        return service.updateConfig(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }
}