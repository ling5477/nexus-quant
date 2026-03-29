package com.guidinglight.nexusquant.auth.application.result;

import java.time.Instant;
import java.util.List;

/**
 * LoginResponse 定义正式登录响应。
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        String username,
        List<String> roles
) {
}

