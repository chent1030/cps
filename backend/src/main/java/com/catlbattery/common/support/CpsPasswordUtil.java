package com.catlbattery.common.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 密码工具：SHA-256(plainPassword + ":" + salt) 十六进制输出。
 *
 * 不依赖 Spring Security；每用户独立 16 字节 salt（32 位 hex）。
 * 当前实现迭代 10000 次以减缓暴力破解（PRD 24.1 安全基线）。
 */
public final class CpsPasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 10_000;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CpsPasswordUtil() { }

    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return toHex(salt);
    }

    public static String hash(String plainPassword, String salt) {
        if (plainPassword == null) plainPassword = "";
        if (salt == null) salt = "";
        String value = plainPassword + ":" + salt;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < ITERATIONS; i++) {
                md.reset();
                bytes = md.digest(bytes);
            }
            return toHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean matches(String plainPassword, String salt, String expectedHash) {
        if (expectedHash == null || expectedHash.isEmpty()) return false;
        String actual = hash(plainPassword, salt);
        return constantTimeEquals(actual, expectedHash);
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
