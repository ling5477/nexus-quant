package com.guidinglight.nexusquant.auth.application;

import com.guidinglight.nexusquant.auth.application.GatewayRequestContext;
import com.guidinglight.nexusquant.auth.domain.TokenClaims;

import java.util.Optional;

/**
 * GatewayAuthFacade 定义网关鉴权占位接口。
 */
public interface GatewayAuthFacade {

    /**
     * @return 当前请求对应的认证主体；未认证时返回 empty。
     */
    Optional<TokenClaims> currentUser();

    /**
     * 校验请求是否允许通过。
     *
     * @param context 网关上下文
     * @return true 表示放行
     */
    boolean allow(GatewayRequestContext context);
}


