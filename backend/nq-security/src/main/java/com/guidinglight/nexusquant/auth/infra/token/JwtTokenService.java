package com.guidinglight.nexusquant.auth.infra.token;

import com.guidinglight.nexusquant.auth.domain.TokenClaims;
import com.guidinglight.nexusquant.auth.domain.port.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JwtTokenService 提供正式 JWT access token 的签发与校验能力。
 * <p>
 * Why:
 * Step 5 的 token 必须可签名、可过期、可校验，不能继续沿用 stub 字符串。
 * 这里统一封装 HMAC JWT 细节，避免登录链和鉴权链各自解析 claim。
 */
public class JwtTokenService implements TokenService {

    private static final String USERNAME_CLAIM = "username";
    private static final String ROLES_CLAIM = "roles";

    private final JwtTokenSettings settings;
    private final Key signingKey;

    public JwtTokenService(JwtTokenSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.signingKey = Keys.hmacShaKeyFor(settings.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issue(TokenClaims claims) {
        return Jwts.builder()
                .subject(claims.subject())
                .issuer(claims.issuer())
                .id(claims.tokenId())
                .issuedAt(Date.from(claims.issuedAt()))
                .expiration(Date.from(claims.expiresAt()))
                .claim(USERNAME_CLAIM, claims.username())
                .claim(ROLES_CLAIM, claims.roles())
                .signWith(signingKey)
                .compact();
    }

    @Override
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(settings.secret().getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!Objects.equals(settings.issuer(), claims.getIssuer())) {
                return Optional.empty();
            }
            Object rolesValue = claims.get(ROLES_CLAIM);
            List<String> roles = rolesValue instanceof List<?> roleList
                    ? roleList.stream().map(String::valueOf).toList()
                    : List.of();
            return Optional.of(new TokenClaims(
                    claims.getSubject(),
                    String.valueOf(claims.get(USERNAME_CLAIM)),
                    roles,
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant(),
                    claims.getIssuer(),
                    claims.getId()
            ));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * @return 当前 access token TTL，用于登录响应构造和测试验证。
     */
    public long accessTokenExpiresInSeconds() {
        return settings.accessTokenTtl().toSeconds();
    }

    /**
     * @return 当前 issuer
     */
    public String issuer() {
        return settings.issuer();
    }
}



