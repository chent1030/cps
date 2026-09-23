package com.company.cps.dto;

/** A4 admin 触发配置更新请求：timeoutSeconds 可空（NULL=回退应用配置）。 */
public class CpsAdminInitialReviewConfigRequest {
    private Boolean autoTriggerEnabled;
    private Integer maxRetryAttempts;
    private Integer retryBackoffMs;
    private Integer timeoutSeconds;
    private String operatorEmpNo;

    public Boolean getAutoTriggerEnabled() { return autoTriggerEnabled; }
    public void setAutoTriggerEnabled(Boolean autoTriggerEnabled) { this.autoTriggerEnabled = autoTriggerEnabled; }
    public Integer getMaxRetryAttempts() { return maxRetryAttempts; }
    public void setMaxRetryAttempts(Integer maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }
    public Integer getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(Integer retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
}
