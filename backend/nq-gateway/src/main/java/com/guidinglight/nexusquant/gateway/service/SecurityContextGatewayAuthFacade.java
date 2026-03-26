package com.guidinglight.nexusquant.gateway.service;

import com.guidinglight.nexusquant.gateway.model.GatewayRequestContext;
import com.guidinglight.nexusquant.security.model.TokenClaims;

import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContextGatewayAuthFacade 提供当前认证态的统一读取入口。
 * <p>
 * Why:
 * Step 5 不再需要 `allow=true` 的假 facade；这里仅作为对 `SecurityContext` 的薄封装，
 * 供 `me` 接口或后续网关层统一读取当前认证主体。
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
