package com.guidinglight.nexusquant.auth.domain;

import java.util.List;
import java.time.Instant;

/**
 * AuthUserProfile 描述 DB-backed auth 使用的最小用户资料。
 */
public record AuthUserProfile(
        Long userId,
        String username,
        String passwordHash,
        List<String> roles,
        boolean enabled,
        boolean mustChangePassword,
        Instant passwordChangedAt,
        long authVersion
) {
    public AuthUserProfile(Long userId, String username, String passwordHash, List<String> roles, boolean enabled) {
        this(userId, username, passwordHash, roles, enabled, false, null, 1);
    }
}
