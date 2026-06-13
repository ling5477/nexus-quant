package com.guidinglight.nexusquant.adapter.okx.service;

import java.util.Locale;
import java.util.Set;

/**
 * OkxPermissionProbeBoundary 固化 OKX permission probe 的 no-order/no-transfer 边界与错误分类。
 *
 * <p>Why: 本轮禁止真实交易所 HTTP，先把 adapter 层允许/禁止 endpoint 和脱敏错误分类做成
 * 可测试边界。未来真实 port 实现只能调用 allowlisted read-only endpoint，且不得把 raw response
 * 传回 Service 或 audit metadata。</p>
 */
public final class OkxPermissionProbeBoundary {

    private static final Set<String> FORBIDDEN_ENDPOINT_TOKENS = Set.of(
            "/trade/order",
            "/trade/cancel",
            "/asset/withdraw",
            "/asset/transfer",
            "/account/transfer"
    );

    private OkxPermissionProbeBoundary() {
    }

    public static boolean isForbiddenEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return true;
        }
        String normalized = endpoint.toLowerCase(Locale.ROOT);
        return FORBIDDEN_ENDPOINT_TOKENS.stream().anyMatch(normalized::contains);
    }

    public static String classify(int httpStatus, String exchangeCode) {
        String normalizedCode = exchangeCode == null ? "" : exchangeCode.trim().toUpperCase(Locale.ROOT);
        if ("HTTP_TIMEOUT".equals(normalizedCode)) {
            return "TIMEOUT";
        }
        if (httpStatus == 429 || "50011".equals(normalizedCode)) {
            return "RATE_LIMITED";
        }
        if (httpStatus >= 500 || "HTTP_CLIENT_ERROR".equals(normalizedCode)) {
            return "EXCHANGE_5XX";
        }
        if ("50113".equals(normalizedCode) || "50110".equals(normalizedCode)) {
            return "AUTH_FAILED";
        }
        if ("50035".equals(normalizedCode)) {
            return "IP_ALLOWLIST_FAILED";
        }
        return "EXCHANGE_ERROR";
    }
}
