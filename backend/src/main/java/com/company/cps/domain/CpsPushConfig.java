package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * F 线推送渠道配置（cps_push_config，单行 GLOBAL）：
 * - 缺失行 / enabled=false → "推送未配置"，记忆体系按此判定；
 * - secret_ref 仅存 KMS/外部密钥引用，密钥明文不入库；
 * - channel 枚举：INTERFACE（本期用）/ EMAIL / SMS / WEBHOOK（预留）。
 */
public class CpsPushConfig {

    public static final String CHANNEL_INTERFACE = "INTERFACE";
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_WEBHOOK = "WEBHOOK";

    public static final long SINGLETON_ID = 1L;

    private Long id;
    private String channel;
    private String endpoint;
    private String secretRef;
    private Boolean enabled;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}