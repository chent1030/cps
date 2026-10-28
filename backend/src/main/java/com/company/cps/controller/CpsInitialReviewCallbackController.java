package com.company.cps.controller;

import com.company.cps.dto.CpsInitialReviewCallbackRequest;
import com.company.cps.service.CpsInitialReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C-02 初审结果回调接收端点。
 *
 * 鉴权模型：与 Python agent 之间不再有 token / IP 白名单校验（用户要求只需配置后端地址与 agent 端地址），
 * 回调来源的可信边界交给部署网络（内网 / 网关层）。
 * 幂等：重复回调不重复写结果（service 层结果唯一键 + 已有结果短路）。
 * 迟到结果：任务已 TAKEN_OVER 时仅置 LATE_RESULT 留痕，不覆盖裁决、不推进流程（PRD §28.4）。
 */
@RestController
@RequestMapping("/api/callbacks")
public class CpsInitialReviewCallbackController {

    private final CpsInitialReviewService initialReviewService;

    public CpsInitialReviewCallbackController(CpsInitialReviewService initialReviewService) {
        this.initialReviewService = initialReviewService;
    }

    @PostMapping("/initial-review/result")
    public ResponseEntity<Map<String, Object>> receiveResult(@RequestBody CpsInitialReviewCallbackRequest payload) {
        Map<String, Object> outcome = initialReviewService.handleCallback(payload);
        return ResponseEntity.ok(outcome);
    }

    /** 未知任务/非法载荷 → 400（区别于服务端内部错误），便于 Python 侧区分重试语义。 */
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", exception.getMessage());
        return ResponseEntity.badRequest().body(body);
    }
}
