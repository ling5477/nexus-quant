package com.guidinglight.nexusquant.security.service;

import java.time.Duration;

/**
 * JwtTokenSettings 描述签发与校验 access token 所需的最小运行时参数。
 */
public record JwtTokenSettings(
        String issuer,
        String secret,
        Duration accessTokenTtl
) {
}
