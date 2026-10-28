package com.catlbattery.common.util;

/**
 * 字符串工具（JDK 8 兼容）。
 *
 * <p>JDK 11 起 {@code String#isBlank()} 才存在，本项目目标 JDK 8，统一走这里，
 * 语义与 JDK 11 的 {@code String#isBlank()} 保持一致（{@link Character#isWhitespace(char)}），
 * 不能用 {@code trim().isEmpty()} 替代：{@code trim()} 只去掉 {@code <= ' '} 的字符，
 * 全角空格等空白会被漏掉。</p>
 */
public final class CpsStrings {

    private CpsStrings() {
    }

    /** null、空串、或全部为空白字符时返回 true。 */
    public static boolean isBlank(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 非空白校验的取反。 */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
