package com.guidinglight.nexusquant.gateway.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.gateway.application.GatewayRequestContext;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextGatewayAuthFacadeTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnEmptyWhenSecurityContextMissing() {
        SecurityContextGatewayAuthFacade facade = new SecurityContextGatewayAuthFacade();

        assertTrue(facade.currentUser().isEmpty());
        assertFalse(facade.allow(new GatewayRequestContext("trace-1", null, "/api/auth/me", "GET")));
    }

    @Test
    void shouldExposeCurrentTokenClaimsFromSecurityContext() {
        TokenClaims claims = new TokenClaims(
                "admin",
                "admin",
                List.of("ADMIN", "OPERATOR"),
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T01:00:00Z"),
                "nexus-quant",
                "jti-1"
        );
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                claims,
                "token",
                List.of()
        ));
        SecurityContextGatewayAuthFacade facade = new SecurityContextGatewayAuthFacade();

        assertEquals("admin", facade.currentUser().orElseThrow().username());
        assertTrue(facade.allow(new GatewayRequestContext("trace-2", "Bearer token", "/api/auth/me", "GET")));
    }
}
