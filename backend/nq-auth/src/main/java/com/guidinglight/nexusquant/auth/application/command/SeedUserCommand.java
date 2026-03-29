package com.guidinglight.nexusquant.auth.application.command;

import java.util.List;

/**
 * SeedUserCommand 表示 local/test seed 与 bootstrap admin 的最小输入。
 */
public record SeedUserCommand(
        String username,
        String passwordHash,
        List<String> roles,
        boolean enabled
) {
}

