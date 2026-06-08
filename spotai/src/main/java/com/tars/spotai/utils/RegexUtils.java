package com.tars.spotai.utils;

import java.util.regex.Pattern;

/**
 * Utility for validating common input formats such as phone numbers and verification codes.
 */
public final class RegexUtils {
    /** Chinese mainland phone number: 11 digits starting with 13-19. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 6-digit code pattern. */
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");

    private RegexUtils() {
    }

    /**
     * Returns true if the phone number is null or does not match the expected format.
     */
    public static boolean isPhoneInvalid(String phone) {
        return phone == null || !PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Returns true if the code is null or is not a valid 6-digit number.
     */
    public static boolean isCodeInvalid(String code) {
        return code == null || !CODE_PATTERN.matcher(code).matches();
    }
}
