package com.company.cps.controller;

import com.company.cps.config.CpsCallbackTrustProperties;
import com.company.cps.dto.CpsInitialReviewCallbackRequest;
import com.company.cps.service.CpsCallbackTrustService;
import com.company.cps.service.CpsInitialReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C-02 初审结果回调接收端点。
 *
 * 安全模型（Phase0 §⑦ internal_trust）：仅校验对端 remoteAddr（不信任 X-Forwarded-For），
 * 可通过 cps.callback.trust-enabled=false 在本机联调时放行。
 * 幂等：重复回调不重复写结果（service 层结果唯一键 + 已有结果短路）。
 * 迟到结果：任务已 TAKEN_OVER 时仅置 LATE_RESULT 留痕，不覆盖裁决、不推进流程（PRD §28.4）。
 */
@RestController
@RequestMapping("/api/callbacks")
public class CpsInitialReviewCallbackController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CpsInitialReviewCallbackController.class);

    private final CpsInitialReviewService initialReviewService;
    private final CpsCallbackTrustService callbackTrustService;
    private final CpsCallbackTrustProperties trustProperties;

    public CpsInitialReviewCallbackController(CpsInitialReviewService initialReviewService,
                                              CpsCallbackTrustService callbackTrustService,
                                              CpsCallbackTrustProperties trustProperties) {
        this.initialReviewService = initialReviewService;
        this.callbackTrustService = callbackTrustService;
        this.trustProperties = trustProperties;
    }

    @PostMapping("/initial-review/result")
    public ResponseEntity<Map<String, Object>> receiveResult(@RequestBody CpsInitialReviewCallbackRequest payload,
                                                             @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
                                                             HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        // 显式忽略 X-Forwarded-For：内网直连模型下该头可伪造，仅取 TCP 对端地址
        if (!callbackTrustService.isTrusted(remoteAddr)) {
            LOGGER.warn("Rejected initial-review callback from untrusted address {} (forwarded-for ignored: {})",
                    remoteAddr, forwardedFor);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "untrusted callback source");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
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
