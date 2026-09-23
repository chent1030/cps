package com.company.cps.domain;

import java.time.LocalDateTime;

/** AI 初审触发配置（cps_initial_review_config，单行 GLOBAL）：自动触发开关 + 技术重试策略可配。 */
public class CpsInitialReviewTriggerConfig {
    private Long id;
    private String configKey;
    private Boolean autoTriggerEnabled;
    private Integer maxRetryAttempts;
    private Integer retryBackoffMs;
    private Integer timeoutSeconds;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public Boolean getAutoTriggerEnabled() { return autoTriggerEnabled; }
    public void setAutoTriggerEnabled(Boolean autoTriggerEnabled) { this.autoTriggerEnabled = autoTriggerEnabled; }
    public Integer getMaxRetryAttempts() { return maxRetryAttempts; }
    public void setMaxRetryAttempts(Integer maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }
    public Integer getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(Integer retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
