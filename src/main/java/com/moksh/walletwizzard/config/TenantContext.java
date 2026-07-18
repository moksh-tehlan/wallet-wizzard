package com.moksh.walletwizzard.config;

import java.util.UUID;

/**
 * Holds the authenticated user's ID for the duration of a request thread.
 * The RLS interceptor reads this to set app.current_user_id in PostgreSQL.
 * Must be cleared after the request completes (handled by the security filter).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_USER = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentUser(UUID userId) {
        CURRENT_USER.set(userId);
    }

    public static UUID getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
