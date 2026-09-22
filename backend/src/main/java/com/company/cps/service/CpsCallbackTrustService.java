package com.company.cps.service;

import com.company.cps.config.CpsCallbackTrustProperties;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * C-02 回调来源校验（internal_trust 纵深防御，Phase 0 §⑦.5）。
 *
 * 信任来源配置 cps.callback.trusted-ips 支持：
 * - 精确 IP（IPv4/IPv6，如 127.0.0.1、::1）；
 * - IPv4 CIDR（如 172.17.0.0/16，覆盖容器化 Python 进程）。
 *
 * 校验的是 TCP 直连对端地址（remoteAddr），不信任 X-Forwarded-For 等可伪造头。
 */
@Service
public class CpsCallbackTrustService {

    private final CpsCallbackTrustProperties properties;

    public CpsCallbackTrustService(CpsCallbackTrustProperties properties) {
        this.properties = properties;
    }

    /** 校验远端地址是否可信；trust-enabled=false 时放行全部（本地联调）。 */
    public boolean isTrusted(String remoteAddr) {
        if (!properties.isTrustEnabled()) {
            return true;
        }
        if (remoteAddr == null || remoteAddr.trim().isEmpty()) {
            return false;
        }
        for (String token : properties.getTrustedIps()) {
            if (matches(remoteAddr, token.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String remoteAddr, String token) {
        if (token.isEmpty()) return false;
        if (token.contains("/")) {
            return matchesCidr(remoteAddr, token);
        }
        return normalizeIp(remoteAddr).equals(normalizeIp(token));
    }

    private boolean matchesCidr(String remoteAddr, String cidr) {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2) return false;
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return false;
        }
        byte[] network = toAddress(parts[0]);
        byte[] remote = toAddress(remoteAddr);
        if (network == null || remote == null || network.length != remote.length || network.length != 4) {
            return false; // CIDR 仅支持 IPv4
        }
        if (prefixLength < 0 || prefixLength > 32) return false;
        int fullBytes = prefixLength / 8;
        int remainderBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (network[i] != remote[i]) return false;
        }
        if (remainderBits > 0 && fullBytes < 4) {
            int mask = 0xFF << (8 - remainderBits);
            if ((network[fullBytes] & mask) != (remote[fullBytes] & mask)) return false;
        }
        return true;
    }

    private byte[] toAddress(String value) {
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    /** 归一化展示形式差异（如 0:0:0:0:0:0:0:1 → ::1），失败原样返回。 */
    private String normalizeIp(String value) {
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException exception) {
            return value;
        }
    }
}
