package com.company.cps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * B6 视觉点检判定服务（Python basic-project /room-checks/judge 端点）可配置端点。
 * 清单①：单 item 视觉判定 60s 超时；不重试（视觉模型本身耗时长，交给异步模型）。
 * 失败/超时：本地状态 TIMEOUT_OPENED 写流水，不抛异常（service 层兜底）。
 */
@Component
@ConfigurationProperties(prefix = "cps.vision.judge")
public class CpsVisionProperties {
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8000";
    private String path = "/api/v1/agent/room-checks/judge";
    private int timeoutMs = 60000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
}