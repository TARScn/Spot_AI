package com.tars.spotai.utils;

import com.tars.spotai.dto.UserDTO;

/**
 * Thread-local holder for the currently authenticated user.
 * Populated by {@link com.tars.spotai.interceptor.RefreshTokenInterceptor} and cleared
 * after request completion.
 */
public final class UserHolder {
    private static final ThreadLocal<UserDTO> USERS = new ThreadLocal<>();

    private UserHolder() {
    }

    /** Stores the current user in the request thread. */
    public static void saveUser(UserDTO user) {
        USERS.set(user);
    }

    /** Returns the current user, or null if not authenticated. */
    public static UserDTO getUser() {
        return USERS.get();
    }

    /** Removes the current user from the thread to prevent memory leaks. */
    public static void removeUser() {
        USERS.remove();
    }
}
