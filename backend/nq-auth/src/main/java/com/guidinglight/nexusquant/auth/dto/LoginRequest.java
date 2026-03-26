package com.guidinglight.nexusquant.auth.dto;

/**
 * LoginRequest 定义最小登录请求。
 */
public record LoginRequest(
        String username,
        String password
) {
}
