package com.company.cps.dto;

/**
 * F 线 admin 推送配置更新请求（PUT 全量替换语义）：
 * - channel 必填（INTERFACE / EMAIL / SMS / WEBHOOK 之一）；
 * - enabled=true 时 endpoint 必填非空（推送渠道启用必须有入口）；
 * - secretRef 仅 KMS/外部密钥引用，明文不入库。
 */
public class CpsPushConfigRequest {
    private String channel;
    private String endpoint;
    private String secretRef;
    private Boolean enabled;
    private String updatedBy;

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
}