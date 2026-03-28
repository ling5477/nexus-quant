package com.guidinglight.nexusquant.auth.domain;

import java.util.List;

/**
 * AuthUserProfile 描述 DB-backed auth 使用的最小用户资料。
 */
public record AuthUserProfile(
        Long userId,
        String username,
        String passwordHash,
        List<String> roles,
        boolean enabled
) {
}
