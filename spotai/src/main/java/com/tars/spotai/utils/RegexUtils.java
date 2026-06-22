package com.tars.spotai.utils;

import java.util.regex.Pattern;

/**
 * Common input validators.
 */
public final class RegexUtils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");

    private RegexUtils() {
    }

    public static boolean isEmailInvalid(String email) {
        return email == null || !EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isCodeInvalid(String code) {
        return code == null || !CODE_PATTERN.matcher(code).matches();
    }
}
