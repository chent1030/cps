package com.company.cps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** B3 辅房点检判定服务（Python C-04 room-checks/judge）可配置端点。 */
@Component
@ConfigurationProperties(prefix = "cps.room-check.judge")
public class CpsRoomCheckJudgeProperties {
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8000";
    private String path = "/api/v1/agent/room-checks/judge";
    /** 清单①：单 item 判定两阶段各 90s 预算（Python 侧），240s 为联调口径（与 C-06 一致），独立于 cps.speech.timeout-ms。 */
    private int timeoutMs = 240000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
}
