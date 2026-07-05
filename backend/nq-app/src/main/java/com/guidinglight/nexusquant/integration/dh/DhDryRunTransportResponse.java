package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunTransportResponse 是 fake transport 返回给 client 的最小响应。
 *
 * @param statusCode HTTP-like status；仅用于解析策略，不代表真实 HTTP 已发生
 * @param body       response body；必须由 client 继续解析和 policy validation
 */
public record DhDryRunTransportResponse(int statusCode, String body) {
}
