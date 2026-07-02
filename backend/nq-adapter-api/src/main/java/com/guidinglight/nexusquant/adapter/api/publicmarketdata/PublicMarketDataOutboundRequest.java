package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.util.Objects;

/**
 * PublicMarketDataOutboundRequest 是进入 policy/client 前的脱敏请求模型。
 *
 * <p>Why: O-1 禁止 service 直接散写 URL。调用方必须先声明 exchange、endpoint category、是否鉴权、
 * 是否签名和诊断字段，再由 policy 决定能否构造 HTTP 请求。该 record 不保存 headers、body 或
 * credential material。</p>
 *
 * @param exchange               exchange/source 名称
 * @param endpointCategory       endpoint 类别
 * @param endpointPath           相对路径；允许 query 进入 HTTP client，但日志层必须脱敏
 * @param requiresAuthentication 是否需要 authentication / API key
 * @param signedRequest          是否需要 signature
 * @param traceId                trace id；可为空
 * @param requestId              request id；可为空
 * @param dataWindow             数据窗口说明；可为空
 */
public record PublicMarketDataOutboundRequest(
        String exchange,
        PublicMarketDataEndpointCategory endpointCategory,
        String endpointPath,
        boolean requiresAuthentication,
        boolean signedRequest,
        String traceId,
        String requestId,
        String dataWindow
) {

    public PublicMarketDataOutboundRequest {
        exchange = normalize(exchange);
        endpointCategory = Objects.requireNonNull(endpointCategory, "endpointCategory must not be null");
        endpointPath = normalizePath(endpointPath);
        traceId = normalizeNullable(traceId);
        requestId = normalizeNullable(requestId);
        dataWindow = normalizeNullable(dataWindow);
    }

    /**
     * 构造最常见的 public GET 请求。
     *
     * @param exchange source/exchange 名称
     * @param category endpoint 类别；必须是 public allowlist 类别才会被 policy 放行
     * @param path     相对路径
     * @return 不带 authentication / signature 的请求模型
     */
    public static PublicMarketDataOutboundRequest publicGet(
            String exchange, PublicMarketDataEndpointCategory category, String path) {
        return new PublicMarketDataOutboundRequest(exchange, category, path, false, false, null, null, null);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim();
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
