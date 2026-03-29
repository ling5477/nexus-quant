package com.guidinglight.nexusquant.auth.domain;

import java.util.List;

/**
 * LocalUserAccount 表示配置驱动的本地账户。
 */
public record LocalUserAccount(
        String username,
        String passwordHash,
        List<String> roles,
        boolean enabled
) {
}

