package com.company.cps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** C-02 回调来源校验配置（cps.callback.*，internal_trust 纵深防御）。 */
@Component
@ConfigurationProperties(prefix = "cps.callback")
public class CpsCallbackTrustProperties {
    /** 校验开关：false 放行全部来源（仅限本地联调）。 */
    private boolean trustEnabled = true;
    /** 信任来源列表：精确 IP（IPv4/IPv6）或 IPv4 CIDR（如 172.17.0.0/16）。 */
    private List<String> trustedIps = Arrays.asList("127.0.0.1", "::1");

    public boolean isTrustEnabled() { return trustEnabled; }
    public void setTrustEnabled(boolean trustEnabled) { this.trustEnabled = trustEnabled; }
    public List<String> getTrustedIps() { return trustedIps; }
    public void setTrustedIps(List<String> trustedIps) { this.trustedIps = trustedIps; }
}
