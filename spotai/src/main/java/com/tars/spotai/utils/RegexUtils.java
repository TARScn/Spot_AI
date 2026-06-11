package com.tars.spotai.utils;

import java.util.regex.Pattern;

/**
 * Utility for validating common input formats such as phone numbers and verification codes.
 */
public final class RegexUtils {
    /* 1. 正则定义 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");  // 中国大陆手机号：11 位，13-19 开头
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");          // 6 位数字验证码

    private RegexUtils() {
    }

    /* 2. 校验工具方法 */
    public static boolean isPhoneInvalid(String phone) {
        /* 手机号为 null 或格式不匹配时返回 true */
        return phone == null || !PHONE_PATTERN.matcher(phone).matches();
    }

    public static boolean isCodeInvalid(String code) {
        /* 验证码为 null 或不是 6 位数字时返回 true */
        return code == null || !CODE_PATTERN.matcher(code).matches();
    }
}
