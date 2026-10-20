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
    /** 跨网络共享密钥：非空时接受 Authorization: Bearer <token>（与 Python 侧 env CPS_CALLBACK_SHARED_TOKEN 配对）；IP 白名单 OR 密钥任一命中即放行。 */
    private String sharedToken = "";

    public boolean isTrustEnabled() { return trustEnabled; }
    public void setTrustEnabled(boolean trustEnabled) { this.trustEnabled = trustEnabled; }
    public List<String> getTrustedIps() { return trustedIps; }
    public void setTrustedIps(List<String> trustedIps) { this.trustedIps = trustedIps; }
    public String getSharedToken() { return sharedToken; }
    public void setSharedToken(String sharedToken) { this.sharedToken = sharedToken; }
}
