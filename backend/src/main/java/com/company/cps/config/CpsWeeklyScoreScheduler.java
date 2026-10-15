package com.company.cps.config;

import com.company.cps.service.CpsWeeklyScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * B4 周评分调度器（PRD §24）：
 * - 默认 cron: "0 59 23 ? * SUN"（每周日 23:59，JVM 默认时区即 Asia/Shanghai 部署口径）；
 * - 关闭开关：cps.weekly-score.scheduler-enabled（默认 true）；
 * - 调度失败不影响下一轮（仅记日志）。
 */
@Component
public class CpsWeeklyScoreScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CpsWeeklyScoreScheduler.class);

    private final CpsWeeklyScoreService weeklyScoreService;

    public CpsWeeklyScoreScheduler(CpsWeeklyScoreService weeklyScoreService) {
        this.weeklyScoreService = weeklyScoreService;
    }

    @Scheduled(cron = "${cps.weekly-score.cron:0 59 23 ? * SUN}")
    public void weeklyRecompute() {
        try {
            int written = weeklyScoreService.recomputeLastNaturalWeek();
            LOGGER.info("Weekly score scheduled recompute wrote {} header(s)", written);
        } catch (Exception exception) {
            LOGGER.error("Weekly score scheduled recompute failed", exception);
        }
    }
}
