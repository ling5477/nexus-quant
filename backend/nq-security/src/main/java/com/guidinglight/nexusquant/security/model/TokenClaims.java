package com.guidinglight.nexusquant.security.model;

import java.time.Instant;
import java.util.List;

/**
 * TokenClaims 定义认证令牌最小声明集合。
 *
 * Why:
 * docs/CONTRACTS.md 指定 JWT 至少需要 sub/username/roles/iat/exp，
 * 骨架阶段先固定模型字段，后续可替换具体 JWT 库实现。
 */
public record TokenClaims(
        String subject,
        String username,
        List<String> roles,
        Instant issuedAt,
        Instant expiresAt
) {
}
