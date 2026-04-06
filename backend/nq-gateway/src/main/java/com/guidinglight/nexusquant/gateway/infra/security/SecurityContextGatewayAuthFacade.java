package com.guidinglight.nexusquant.gateway.infra.security;

import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.gateway.application.GatewayRequestContext;

import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContextGatewayAuthFacade 提供当前认证态的统一读取入口。
 * <p>
 * Why:
 * 该实现属于安全基础设施层，应该挂在 `gateway.infra.security`，而不是继续和
 * `nq-auth` 共用 `auth.infra.*` 命名空间。
 */
public class SecurityContextGatewayAuthFacade implements GatewayAuthFacade {

    @Override
    public Optional<TokenClaims> currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof TokenClaims claims ? Optional.of(claims) : Optional.empty();
    }

    @Override
    public boolean allow(GatewayRequestContext context) {
        return currentUser().isPresent();
    }
}



