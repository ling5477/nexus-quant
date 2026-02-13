package com.guidinglight.nexusquant.gateway.service;

import com.guidinglight.nexusquant.gateway.model.GatewayRequestContext;

/**
 * NoopGatewayAuthFacade 提供默认放行占位实现。
 *
 * Why:
 * Gate A 阶段先冻结接口，不在此处引入复杂鉴权规则。
 */
public class NoopGatewayAuthFacade implements GatewayAuthFacade {

    @Override
    public boolean allow(GatewayRequestContext context) {
        return true;
    }
}
