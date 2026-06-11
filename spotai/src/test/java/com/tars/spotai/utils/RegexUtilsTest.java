package com.tars.spotai.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RegexUtils} — verifies phone number and verification code validation.
 */
class RegexUtilsTest {

    /* 1. 手机号格式校验 */
    @Test
    void validatesMainlandPhoneNumbers() {
        assertThat(RegexUtils.isPhoneInvalid("13800138000")).isFalse();  // 合法
        assertThat(RegexUtils.isPhoneInvalid("12800138000")).isTrue();   // 12 开头 → 非法
        assertThat(RegexUtils.isPhoneInvalid("1380013800")).isTrue();    // 10 位 → 非法
        assertThat(RegexUtils.isPhoneInvalid(null)).isTrue();            // null → 非法
    }

    /* 2. 验证码格式校验 */
    @Test
    void validatesSixDigitCodes() {
        assertThat(RegexUtils.isCodeInvalid("123456")).isFalse();  // 6 位数字 → 合法
        assertThat(RegexUtils.isCodeInvalid("12345")).isTrue();    // 5 位 → 非法
        assertThat(RegexUtils.isCodeInvalid("abcdef")).isTrue();   // 字母 → 非法
    }
}
