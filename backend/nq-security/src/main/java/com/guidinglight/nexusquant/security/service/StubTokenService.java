package com.guidinglight.nexusquant.security.service;

import com.guidinglight.nexusquant.security.model.TokenClaims;
import java.util.Optional;

/**
 * StubTokenService 是 Gate A 的占位实现。
 *
 * Why:
 * 当前阶段只要求模块边界可编译可装配，不要求生产级 JWT 安全能力。
 * 后续可无缝替换为 JJWT/Nimbus 实现。
 */
public class StubTokenService implements TokenService {

    @Override
    public String issue(TokenClaims claims) {
        return "stub-token-" + claims.subject();
    }

    @Override
    public Optional<TokenClaims> parse(String token) {
        return Optional.empty();
    }
}
