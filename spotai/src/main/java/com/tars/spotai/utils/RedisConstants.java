package com.tars.spotai.utils;

/**
 * Centralized constants for Redis key prefixes used across the application.
 */
public final class RedisConstants {
    /** Key prefix for cached login verification codes (suffix: phone number). */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /** Key prefix for cached login tokens (suffix: token string). */
    public static final String LOGIN_TOKEN_KEY = "login:token:";

    private RedisConstants() {
    }
}
