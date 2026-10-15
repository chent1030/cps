package com.company.cps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * F3 语音转写服务（Python C-06 speech-to-text，同步）可配置端点。
 *
 * 联调清单口径：转写为现场同步等待，超时给到 240s 量级（可配）；
 * 未启用/未配置/失败一律降级返回文案，不阻塞移动端表单填写。
 */
@Component
@ConfigurationProperties(prefix = "cps.speech")
public class CpsSpeechProperties {
    private boolean enabled = true;
    /** 默认 8000 端口对齐 Python 服务实际监听。 */
    private String baseUrl = "http://127.0.0.1:8000";
    private String path = "/api/v1/agent/speech-to-text";
    /** 同步等待超时（毫秒）：240s 量级，可配。 */
    private int timeoutMs = 240000;
    /** 音频原始文件大小上限（字节），与 multipart 限制同口径。 */
    private long maxAudioBytes = 20L * 1024 * 1024;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public long getMaxAudioBytes() { return maxAudioBytes; }
    public void setMaxAudioBytes(long maxAudioBytes) { this.maxAudioBytes = maxAudioBytes; }
}
