package com.company.cps.config;

import com.company.cps.service.CpsInitialReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 初审任务超时扫描（PRD §28.4 / 详细设计 §3.1）：
 * - RUNNING 且 now >= timeout_at → TIMEOUT_OPEN（超时可接管，区别于执行失败）
 * - PENDING_REVIEWER_CONFIG 下已终态的任务，在审核员配置补齐后续路（AC-25 不要求重新提交）
 *
 * 开关：cps.initial-review.scan-enabled（默认 true）；@EnableScheduling 见 CpsBackendApplication。
 */
@Component
@ConditionalOnProperty(name = "cps.initial-review.scan-enabled", havingValue = "true", matchIfMissing = true)
public class CpsInitialReviewScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CpsInitialReviewScheduler.class);

    private final CpsInitialReviewService initialReviewService;

    public CpsInitialReviewScheduler(CpsInitialReviewService initialReviewService) {
        this.initialReviewService = initialReviewService;
    }

    @Scheduled(fixedDelayString = "${cps.initial-review.scan-interval-ms:30000}", initialDelayString = "${cps.initial-review.scan-initial-delay-ms:15000}")
    public void scan() {
        try {
            int timedOut = initialReviewService.timeoutScan();
            if (timedOut > 0) {
                LOGGER.info("Initial review timeout scan advanced {} task(s)", timedOut);
            }
        } catch (Exception exception) {
            // 扫描失败不影响下一轮；数据库异常等由日志承接
            LOGGER.error("Initial review timeout scan failed", exception);
        }
    }
}
