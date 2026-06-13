package com.guidinglight.nexusquant.adapter.binance.service;

import java.util.Locale;
import java.util.Set;

/**
 * BinancePermissionProbeBoundary 固化 Binance permission probe 的 no-order/no-transfer 边界与错误分类。
 *
 * <p>Why: 本轮只实现 adapter 层可测试边界，不接真实 Binance HTTP。未来真实 port 实现只能调用
 * allowlisted read-only endpoint，并且不得把 signature、headers、raw response 或 credential material
 * 放入 ProbeResult、日志或 audit metadata。</p>
 */
public final class BinancePermissionProbeBoundary {

    private static final Set<String> FORBIDDEN_ENDPOINT_TOKENS = Set.of(
            "/api/v3/order",
            "/sapi",
            "transfer",
            "withdraw"
    );

    private BinancePermissionProbeBoundary() {
    }

    public static boolean isForbiddenEndpoint(String method, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return true;
        }
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        String normalizedEndpoint = endpoint.toLowerCase(Locale.ROOT);
        if ("POST".equals(normalizedMethod) && normalizedEndpoint.contains("/api/v3/order")) {
            return true;
        }
        if ("DELETE".equals(normalizedMethod) && normalizedEndpoint.contains("/api/v3/order")) {
            return true;
        }
        return FORBIDDEN_ENDPOINT_TOKENS.stream().anyMatch(normalizedEndpoint::contains);
    }

    public static String classify(int httpStatus, String exchangeCode) {
        String normalizedCode = exchangeCode == null ? "" : exchangeCode.trim().toUpperCase(Locale.ROOT);
        if ("HTTP_TIMEOUT".equals(normalizedCode)) {
            return "TIMEOUT";
        }
        if (httpStatus == 429 || httpStatus == 418 || "-1003".equals(normalizedCode)) {
            return "RATE_LIMITED";
        }
        if (httpStatus >= 500 || "HTTP_CLIENT_ERROR".equals(normalizedCode)) {
            return "EXCHANGE_5XX";
        }
        if ("-2015".equals(normalizedCode) || "-2014".equals(normalizedCode) || "-1022".equals(normalizedCode)) {
            return "AUTH_FAILED";
        }
        if ("IP_RESTRICTED".equals(normalizedCode)) {
            return "IP_ALLOWLIST_FAILED";
        }
        return "EXCHANGE_ERROR";
    }
}
