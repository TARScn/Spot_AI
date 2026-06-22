package com.tars.spotai.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegexUtilsTest {
    @Test
    void validatesEmailAddresses() {
        assertThat(RegexUtils.isEmailInvalid("spotai@qq.com")).isFalse();
        assertThat(RegexUtils.isEmailInvalid("user.name+tag@example.com")).isFalse();
        assertThat(RegexUtils.isEmailInvalid("bad-email")).isTrue();
        assertThat(RegexUtils.isEmailInvalid("@example.com")).isTrue();
        assertThat(RegexUtils.isEmailInvalid(null)).isTrue();
    }

    @Test
    void validatesSixDigitCodes() {
        assertThat(RegexUtils.isCodeInvalid("123456")).isFalse();
        assertThat(RegexUtils.isCodeInvalid("12345")).isTrue();
        assertThat(RegexUtils.isCodeInvalid("abcdef")).isTrue();
    }
}
