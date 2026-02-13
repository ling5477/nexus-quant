package com.guidinglight.nexusquant.auth.service;

import com.guidinglight.nexusquant.auth.dto.LoginRequest;
import com.guidinglight.nexusquant.auth.dto.LoginResponse;
import com.guidinglight.nexusquant.security.model.TokenClaims;
import com.guidinglight.nexusquant.security.service.TokenService;
import java.time.Instant;
import java.util.List;

/**
 * NoopAuthService 提供最小可装配登录占位实现。
 *
 * Why:
 * Gate A 仅要求登录契约与模块边界固定，不实现真实用户校验和权限管理。
 */
public class NoopAuthService implements AuthService {

    private final TokenService tokenService;

    public NoopAuthService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        Instant now = Instant.now();
        TokenClaims claims = new TokenClaims("demo-user-id", request.username(), List.of("ADMIN"), now, now.plusSeconds(3600));
        return new LoginResponse(tokenService.issue(claims), "Bearer", 3600L);
    }
}
