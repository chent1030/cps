package com.company.cps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** V2 整改域 AI 初审配置（cps.initial-review.*）。 */
@Component
@ConfigurationProperties(prefix = "cps.initial-review")
public class CpsInitialReviewProperties {
    /** 超时扫描总开关（false 时停用 @Scheduled 扫描，仅保留回调驱动）。 */
    private boolean scanEnabled = true;
    /** 扫描间隔，默认 30s。 */
    private long scanIntervalMs = 30000L;
    /** 人工接管阈值（秒），默认 600s=10 分钟（PRD §28.4）。 */
    private int timeoutSeconds = 600;
    /** C-01 投递给 Python 的回调地址（C-02 端点）。 */
    private String callbackBaseUrl = "http://127.0.0.1:8080";

    public boolean isScanEnabled() { return scanEnabled; }
    public void setScanEnabled(boolean scanEnabled) { this.scanEnabled = scanEnabled; }
    public long getScanIntervalMs() { return scanIntervalMs; }
    public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getCallbackBaseUrl() { return callbackBaseUrl; }
    public void setCallbackBaseUrl(String callbackBaseUrl) { this.callbackBaseUrl = callbackBaseUrl; }
}
