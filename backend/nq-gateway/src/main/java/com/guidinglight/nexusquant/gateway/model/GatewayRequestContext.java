package com.guidinglight.nexusquant.gateway.model;

/**
 * GatewayRequestContext 表示网关层最小请求上下文。
 */
public record GatewayRequestContext(
        String traceId,
        String authorizationHeader,
        String path,
        String method
) {
}
