package com.guidinglight.nexusquant.gateway.service;

import com.guidinglight.nexusquant.gateway.model.GatewayRequestContext;

/**
 * GatewayAuthFacade 定义网关鉴权占位接口。
 */
public interface GatewayAuthFacade {

    /**
     * 校验请求是否允许通过。
     *
     * @param context 网关上下文
     * @return true 表示放行
     */
    boolean allow(GatewayRequestContext context);
}
