package com.company.cps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** B3 辅房点检判定服务（Python C-04 room-checks/judge）可配置端点。 */
@Component
@ConfigurationProperties(prefix = "cps.room-check.judge")
public class CpsRoomCheckJudgeProperties {
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8010";
    private String path = "/api/agent/room-checks/judge";
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
