package com.guidinglight.nexusquant.gateway.application;

import com.guidinglight.nexusquant.security.token.TokenClaims;

import java.util.Optional;

/**
 * GatewayAuthFacade 定义认证上下文读取与网关放行判定入口。
 * <p>
 * Why:
 * `nq-gateway` 必须输出独立 package，避免继续和 `nq-auth` 共享
 * `auth.application` 命名空间并形成 split package。
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


