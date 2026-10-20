package com.company.cps.service;

import com.company.cps.config.CpsCallbackTrustProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C-02 来源校验：精确 IP / IPv4 CIDR / 联调放行开关。 */
class CpsCallbackTrustServiceTest {

    @Test
    void exactIpMatchWithNormalization() {
        CpsCallbackTrustService service = service(true, "127.0.0.1", "::1");
        assertTrue(service.isTrusted("127.0.0.1"));
        assertTrue(service.isTrusted("0:0:0:0:0:0:0:1")); // IPv6 展开形式归一化为 ::1
        assertTrue(service.isTrusted("::1"));
        assertFalse(service.isTrusted("10.0.0.5"));
        assertFalse(service.isTrusted(null));
        assertFalse(service.isTrusted("  "));
    }

    @Test
    void cidrRangeMatch() {
        CpsCallbackTrustService service = service(true, "172.17.0.0/16");
        assertTrue(service.isTrusted("172.17.0.1"));
        assertTrue(service.isTrusted("172.17.255.254"));
        assertFalse(service.isTrusted("172.18.0.1"));
        assertFalse(service.isTrusted("172.16.0.1"));
    }

    @Test
    void cidrPrefixBoundaryRespected() {
        CpsCallbackTrustService service = service(true, "10.1.2.0/26");
        assertTrue(service.isTrusted("10.1.2.63"));
        assertFalse(service.isTrusted("10.1.2.64"));
    }

    @Test
    void disabledTrustAllowsAllForLocalDebug() {
        CpsCallbackTrustService service = service(false, "127.0.0.1");
        assertTrue(service.isTrusted("203.0.113.9"));
    }

    @Test
    void sharedTokenAcceptsUnlistedIp() {
        CpsCallbackTrustService service = service(true, "127.0.0.1");
        setSharedToken("s3cret-key");
        // IP 不在白名单，但 Bearer 密钥命中 → 放行（跨网络部署路径）
        assertTrue(service.isTrusted("203.0.113.9", "Bearer s3cret-key"));
        // 白名单 IP 无需密钥仍放行（OR 语义）
        assertTrue(service.isTrusted("127.0.0.1", null));
    }

    @Test
    void sharedTokenRejectsWrongOrMissingCredentials() {
        CpsCallbackTrustService service = service(true, "127.0.0.1");
        setSharedToken("s3cret-key");
        assertFalse(service.isTrusted("203.0.113.9", "Bearer wrong-key"));
        assertFalse(service.isTrusted("203.0.113.9", "s3cret-key")); // 缺 Bearer 前缀
        assertFalse(service.isTrusted("203.0.113.9", null));
        assertFalse(service.isTrusted("203.0.113.9", "Bearer s3cret-key-with-suffix"));
    }

    @Test
    void emptySharedTokenMeansHeaderIgnored() {
        CpsCallbackTrustService service = service(true, "127.0.0.1");
        // 未配置密钥：即使带了任意 Bearer 头也不放行非白名单 IP
        assertFalse(service.isTrusted("203.0.113.9", "Bearer anything"));
    }

    private CpsCallbackTrustProperties properties;

    /** 给最近一次 service(...) 构造的 properties 配置共享密钥（service 持有同一实例）。 */
    private void setSharedToken(String token) {
        properties.setSharedToken(token);
    }

    private CpsCallbackTrustService service(boolean enabled, String... trustedIps) {
        properties = new CpsCallbackTrustProperties();
        properties.setTrustEnabled(enabled);
        properties.setTrustedIps(Arrays.asList(trustedIps));
        return new CpsCallbackTrustService(properties);
    }
}
