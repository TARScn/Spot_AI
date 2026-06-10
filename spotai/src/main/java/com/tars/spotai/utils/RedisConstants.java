package com.tars.spotai.utils;

/**
 * Centralized constants for Redis key prefixes used across the application.
 */
public final class RedisConstants {
    /** Key prefix for cached login verification codes (suffix: phone number). */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /** Key prefix for cached login tokens (suffix: token string). */
    public static final String LOGIN_TOKEN_KEY = "login:token:";

    /** Key prefix for cached shop details (suffix: shop ID). */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    /** TTL for cached shop details, in minutes. */
    public static final long CACHE_SHOP_TTL_MINUTES = 30L;

    /** Max random extra TTL for ordinary caches, in seconds. */
    public static final long CACHE_RANDOM_TTL_SECONDS = 300L;

    /** Key prefix for cache rebuild locks (suffix: resource ID). */
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    /** TTL for shop cache rebuild locks, in seconds. */
    public static final long LOCK_SHOP_TTL_SECONDS = 10L;

    /** Default TTL for cache rebuild locks, in seconds. */
    public static final long CACHE_REBUILD_LOCK_TTL_SECONDS = 10L;

    /** TTL for cached empty shop values, in minutes. */
    public static final long CACHE_NULL_TTL_MINUTES = 2L;

    private RedisConstants() {
    }
}
