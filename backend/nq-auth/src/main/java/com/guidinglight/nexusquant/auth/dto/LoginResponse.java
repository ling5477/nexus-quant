package com.guidinglight.nexusquant.auth.dto;

/**
 * LoginResponse 定义最小登录响应。
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
