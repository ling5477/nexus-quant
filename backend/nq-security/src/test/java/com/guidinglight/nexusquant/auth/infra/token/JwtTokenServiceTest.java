package com.guidinglight.nexusquant.auth.infra.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.auth.domain.TokenClaims;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * JwtTokenServiceTest 验证 JWT 签发、篡改检测、过期校验与角色解析。
 */
class JwtTokenServiceTest {

    private final JwtTokenService tokenService = new JwtTokenService(
            new JwtTokenSettings("nexus-quant-test", "test-change-me-test-change-me-123456", Duration.ofMinutes(30))
    );

    @Test
    void shouldIssueAndParseToken() {
        Instant now = Instant.now();
        TokenClaims claims = new TokenClaims("u-1", "admin", List.of("ADMIN", "OPERATOR"), now, now.plusSeconds(1800), "nexus-quant-test", "jti-1");

        String token = tokenService.issue(claims);
        var parsed = tokenService.parse(token);

        assertTrue(parsed.isPresent());
        assertEquals("admin", parsed.get().username());
        assertEquals(List.of("ADMIN", "OPERATOR"), parsed.get().roles());
        assertEquals("nexus-quant-test", parsed.get().issuer());
    }

    @Test
    void shouldRejectTamperedToken() {
        Instant now = Instant.now();
        String token = tokenService.issue(new TokenClaims(
                "u-1",
                "admin",
                List.of("ADMIN"),
                now,
                now.plusSeconds(1800),
                "nexus-quant-test",
                "jti-2"
        ));

        assertFalse(tokenService.parse(token + "tampered").isPresent());
    }

    @Test
    void shouldRejectExpiredToken() {
        Instant now = Instant.now().minusSeconds(3600);
        String token = tokenService.issue(new TokenClaims(
                "u-1",
                "admin",
                List.of("ADMIN"),
                now,
                now.plusSeconds(10),
                "nexus-quant-test",
                "jti-3"
        ));

        assertFalse(tokenService.parse(token).isPresent());
    }
}


