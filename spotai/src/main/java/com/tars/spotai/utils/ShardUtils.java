package com.tars.spotai.utils;

/**
 * Utility for database sharding based on phone number.
 * Provides consistent table name resolution across the application.
 */
public final class ShardUtils {
    private ShardUtils() {
    }

    /**
     * Returns a shard index (0 or 1) derived from the phone number's hash code.
     */
    public static int phoneShard(String phone) {
        return Math.floorMod(phone.hashCode(), 2);
    }

    /**
     * Returns the user table name for the given phone number.
     */
    public static String userTable(String phone) {
        return "tb_user_" + phoneShard(phone);
    }

    /**
     * Returns the phone-index table name for the given phone number.
     */
    public static String userPhoneTable(String phone) {
        return "tb_user_phone_" + phoneShard(phone);
    }
}
