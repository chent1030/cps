package com.company.cps.service;

import com.company.cps.domain.CpsInitialReviewEvent;
import com.company.cps.domain.CpsInitialReviewTask;
import com.company.cps.domain.CpsInitialReviewTriggerConfig;
import com.company.cps.domain.CpsReviewAdjudication;
import com.company.cps.dto.CpsAdminInitialReviewConfigRequest;
import com.company.cps.dto.CpsInitialReviewAdminTaskView;
import com.company.cps.mapper.CpsInitialReviewConfigMapper;
import com.company.cps.mapper.CpsInitialReviewEventMapper;
import com.company.cps.mapper.CpsInitialReviewItemMapper;
import com.company.cps.mapper.CpsInitialReviewResultMapper;
import com.company.cps.mapper.CpsInitialReviewTaskMapper;
import com.company.cps.mapper.CpsReviewAdjudicationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A4 初审触发管理（PRD §28.4/§29.3，D-21）：
 * - 触发配置：提交时自动触发开关（关=仅建任务 PENDING_DISPATCH 待手动触发）+
 *   投递技术重试策略（次数/退避）+ 接管阈值秒，DB 单行 GLOBAL，运行时生效；
 * - 触发记录：触发/回调/超时接管/重触发/裁决事件流水查询；
 * - 失败任务可手动重触发（委托 CpsInitialReviewService.retrigger）。
 */
@Service
public class CpsInitialReviewAdminService {

    static final String CONFIG_KEY_GLOBAL = "GLOBAL";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 校验边界与迁移注释一致：重试 0..5、退避 0..60000ms、超时 30..86400s（NULL=回退应用配置）。 */
    private static final int MAX_RETRY_ATTEMPTS_LIMIT = 5;
    private static final int RETRY_BACKOFF_MS_LIMIT = 60_000;
    private static final int TIMEOUT_SECONDS_MIN = 30;
    private static final int TIMEOUT_SECONDS_MAX = 86_400;

    private final CpsInitialReviewConfigMapper configMapper;
    private final CpsInitialReviewTaskMapper taskMapper;
    private final CpsInitialReviewResultMapper resultMapper;
    private final CpsInitialReviewItemMapper itemMapper;
    private final CpsInitialReviewEventMapper eventMapper;
    private final CpsReviewAdjudicationMapper adjudicationMapper;
    private final CpsInitialReviewService initialReviewService;

    public CpsInitialReviewAdminService(
            CpsInitialReviewConfigMapper configMapper,
            CpsInitialReviewTaskMapper taskMapper,
            CpsInitialReviewResultMapper resultMapper,
            CpsInitialReviewItemMapper itemMapper,
            CpsInitialReviewEventMapper eventMapper,
            CpsReviewAdjudicationMapper adjudicationMapper,
            CpsInitialReviewService initialReviewService
    ) {
        this.configMapper = configMapper;
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.itemMapper = itemMapper;
        this.eventMapper = eventMapper;
        this.adjudicationMapper = adjudicationMapper;
        this.initialReviewService = initialReviewService;
    }

    /** 读取触发配置（GLOBAL 单行；无行时返回缺省口径）。 */
    public Map<String, Object> getConfig() {
        Map<String, Object> response = new LinkedHashMap<>();
        CpsInitialReviewTriggerConfig config = configMapper.findGlobal();
        response.put("config_key", CONFIG_KEY_GLOBAL);
        response.put("auto_trigger_enabled", config == null || config.getAutoTriggerEnabled() == null
                ? Boolean.TRUE : config.getAutoTriggerEnabled());
        response.put("max_retry_attempts", config == null || config.getMaxRetryAttempts() == null
                ? 1 : config.getMaxRetryAttempts());
        response.put("retry_backoff_ms", config == null || config.getRetryBackoffMs() == null
                ? 3000 : config.getRetryBackoffMs());
        // NULL=未覆盖，回退应用配置 cps.initial-review.timeout-seconds（600s，AC-27）
        response.put("timeout_seconds", config == null ? null : config.getTimeoutSeconds());
        response.put("timeout_source", config == null || config.getTimeoutSeconds() == null
                ? "application-config" : "database");
        response.put("updated_by", config == null ? null : config.getUpdatedBy());
        response.put("updated_at", config == null ? null : config.getUpdatedAt());
        return response;
    }

    /**
     * 更新触发配置（PUT 全量替换语义：autoTrigger/maxRetry/backoff 必填；
     * timeoutSeconds 可为 NULL=清除覆盖，回退应用配置 cps.initial-review.timeout-seconds）。
     */
    @Transactional
    public Map<String, Object> updateConfig(CpsAdminInitialReviewConfigRequest request) {
        if (request.getAutoTriggerEnabled() == null) {
            throw new IllegalArgumentException("autoTriggerEnabled is required");
        }
        if (request.getMaxRetryAttempts() == null) {
            throw new IllegalArgumentException("maxRetryAttempts is required");
        }
        if (request.getRetryBackoffMs() == null) {
            throw new IllegalArgumentException("retryBackoffMs is required");
        }
        if (request.getMaxRetryAttempts() < 0 || request.getMaxRetryAttempts() > MAX_RETRY_ATTEMPTS_LIMIT) {
            throw new IllegalArgumentException("maxRetryAttempts must be within 0.." + MAX_RETRY_ATTEMPTS_LIMIT);
        }
        if (request.getRetryBackoffMs() < 0 || request.getRetryBackoffMs() > RETRY_BACKOFF_MS_LIMIT) {
            throw new IllegalArgumentException("retryBackoffMs must be within 0.." + RETRY_BACKOFF_MS_LIMIT);
        }
        if (request.getTimeoutSeconds() != null
                && (request.getTimeoutSeconds() < TIMEOUT_SECONDS_MIN
                || request.getTimeoutSeconds() > TIMEOUT_SECONDS_MAX)) {
            throw new IllegalArgumentException(
                    "timeoutSeconds must be within " + TIMEOUT_SECONDS_MIN + ".." + TIMEOUT_SECONDS_MAX
                            + " or null (fall back to application config)");
        }
        String operator = request.getOperatorEmpNo() == null || request.getOperatorEmpNo().trim().isEmpty()
                ? "ADMIN" : request.getOperatorEmpNo().trim();
        CpsInitialReviewTriggerConfig current = configMapper.findGlobal();
        CpsInitialReviewTriggerConfig config = current == null ? new CpsInitialReviewTriggerConfig() : current;
        config.setConfigKey(CONFIG_KEY_GLOBAL);
        config.setAutoTriggerEnabled(request.getAutoTriggerEnabled());
        config.setMaxRetryAttempts(request.getMaxRetryAttempts());
        config.setRetryBackoffMs(request.getRetryBackoffMs());
        config.setTimeoutSeconds(request.getTimeoutSeconds());
        config.setUpdatedBy(operator);
        config.setUpdatedAt(LocalDateTime.now());
        int updated = configMapper.updateGlobal(config);
        if (updated == 0) {
            // 兜底：GLOBAL 行缺失（正常由迁移种子）时自愈插入
            configMapper.insertGlobal(config);
        }
        return getConfig();
    }

    /** 触发记录分页：可选 status/issueId 过滤，含问题状态/AI 结果/裁决关联视图。 */
    public Map<String, Object> listTasks(String status, Long issueId, int page, int pageSize) {
        int safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int safePage = page <= 0 ? 1 : page;
        long total = taskMapper.countAdminPage(status, issueId);
        List<CpsInitialReviewAdminTaskView> tasks = taskMapper.findAdminPage(
                status, issueId, safePageSize, (safePage - 1) * safePageSize);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", safePage);
        response.put("page_size", safePageSize);
        response.put("total", total);
        response.put("tasks", tasks);
        return response;
    }

    /** 触发任务详情：任务 + AI 结果 + 逐项意见 + 事件流水 + 既有裁决。 */
    public Map<String, Object> taskDetail(Long taskId) {
        CpsInitialReviewTask task = taskMapper.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Initial review task not found: " + taskId);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task", task);
        response.put("result", resultMapper.findByTaskId(taskId));
        response.put("items", itemMapper.findByTaskId(taskId));
        List<CpsInitialReviewEvent> events = eventMapper.findByTaskId(taskId);
        response.put("events", events);
        response.put("adjudication", adjudicationMapper.findByIssueAndVersion(task.getIssueId(), task.getVersionNo()));
        return response;
    }

    /** 手动重触发失败任务（委托 CpsInitialReviewService.retrigger，规则见其 javadoc）。 */
    public Map<String, Object> retrigger(Long taskId, String operatorEmpNo, String reason) {
        return initialReviewService.retrigger(taskId, operatorEmpNo, reason);
    }
}
