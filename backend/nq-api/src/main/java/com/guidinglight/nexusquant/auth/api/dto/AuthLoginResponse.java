package com.guidinglight.nexusquant.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * AuthLoginResponse 描述正式登录成功响应。
 */
@Schema(name = "AuthLoginResponse", description = "登录成功响应")
public record AuthLoginResponse(
        @Schema(description = "访问令牌")
        String accessToken,
        @Schema(description = "令牌类型，固定 Bearer")
        String tokenType,
        @Schema(description = "过期秒数")
        long expiresIn,
        @Schema(description = "过期时间")
        Instant expiresAt,
        @Schema(description = "用户名")
        String username,
        @Schema(description = "角色列表")
        List<String> roles
) {
}


