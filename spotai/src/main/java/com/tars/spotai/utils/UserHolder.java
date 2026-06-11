package com.tars.spotai.utils;

import com.tars.spotai.dto.UserDTO;

/**
 * Thread-local holder for the currently authenticated user.
 * Populated by {@link com.tars.spotai.interceptor.RefreshTokenInterceptor} and cleared
 * after request completion.
 */
public final class UserHolder {
    /* 1. 存储介质：ThreadLocal */
    private static final ThreadLocal<UserDTO> USERS = new ThreadLocal<>();

    private UserHolder() {
    }

    /* 2. 将当前用户存入线程 */
    public static void saveUser(UserDTO user) {
        USERS.set(user);
    }

    /* 3. 获取当前线程中的用户（未认证时返回 null） */
    public static UserDTO getUser() {
        return USERS.get();
    }

    /* 4. 请求结束后清理，防止内存泄漏 */
    public static void removeUser() {
        USERS.remove();
    }
}
