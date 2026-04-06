package com.guidinglight.nexusquant.security.token;

import java.time.Instant;
import java.util.List;

/**
 * TokenClaims 定义正式 access token 的最小声明集合。
 * <p>
 * Why:
 * Step 5 要把 stub token 替换为真实 JWT，因此需要显式固定 `sub/roles/iat/exp/iss/jti`
 * 这些认证链最小可用字段，避免各层自行拼 claim 造成解析和授权口径漂移。
 */
public record TokenClaims(
        String subject,
        String username,
        List<String> roles,
        Instant issuedAt,
        Instant expiresAt,
        String issuer,
        String tokenId
) {
}

