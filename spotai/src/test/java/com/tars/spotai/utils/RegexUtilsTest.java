package com.tars.spotai.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RegexUtils} — verifies phone number and verification code validation.
 */
class RegexUtilsTest {

    @Test
    void validatesMainlandPhoneNumbers() {
        assertThat(RegexUtils.isPhoneInvalid("13800138000")).isFalse();
        assertThat(RegexUtils.isPhoneInvalid("12800138000")).isTrue();
        assertThat(RegexUtils.isPhoneInvalid("1380013800")).isTrue();
        assertThat(RegexUtils.isPhoneInvalid(null)).isTrue();
    }

    @Test
    void validatesSixDigitCodes() {
        assertThat(RegexUtils.isCodeInvalid("123456")).isFalse();
        assertThat(RegexUtils.isCodeInvalid("12345")).isTrue();
        assertThat(RegexUtils.isCodeInvalid("abcdef")).isTrue();
    }
}
