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

    private CpsCallbackTrustService service(boolean enabled, String... trustedIps) {
        CpsCallbackTrustProperties properties = new CpsCallbackTrustProperties();
        properties.setTrustEnabled(enabled);
        properties.setTrustedIps(Arrays.asList(trustedIps));
        return new CpsCallbackTrustService(properties);
    }
}
