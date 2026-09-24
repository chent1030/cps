package com.company.cps.controller;

import com.company.cps.dto.CpsScoringRuleRequest;
import com.company.cps.service.CpsScoringRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * B8 评分规则（PRD §23.1 base-factory 口径）：
 * - GET /api/cps/admin/scoring-rules：列出全部规则（全局 + 工厂校准）；
 * - PUT /api/cps/admin/scoring-rules：upsert 单条规则（按 rule_key+factory UNIQUE）。
 *
 * 鉴权由部署层企业 SSO/网关统一注入（沿用波次 2 管理端口径）。
 */
@RestController
@RequestMapping("/api/cps/admin/scoring-rules")
public class CpsScoringRuleController {

    private final CpsScoringRuleService service;

    public CpsScoringRuleController(CpsScoringRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.listAll();
    }

    @PutMapping
    public Map<String, Object> upsert(@RequestBody CpsScoringRuleRequest request) {
        return service.upsert(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }
}
