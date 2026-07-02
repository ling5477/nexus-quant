package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.util.Locale;
import java.util.Set;

/**
 * PublicMarketDataRedactor 提供 O-1 日志与 summary 的最小脱敏规则。
 *
 * <p>Why: fake server 或未来 public endpoint 即使返回 credential-like 字段，也不能进入日志、response
 * summary 或异常 message。本类只保留安全字段，删除 query string，并替换 credential/token/signature
 * 类关键词后的内容。</p>
 */
public final class PublicMarketDataRedactor {

    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "apikey",
            "api_key",
            "api-key",
            "secret",
            "passphrase",
            "signature",
            "token",
            "authorization",
            "cookie",
            "set-cookie"
    );

    private PublicMarketDataRedactor() {
    }

    /**
     * 移除 endpoint path 的 query string。
     *
     * @param endpointPath 原始相对路径
     * @return 不含 full query string 的路径；空值返回 "/"
     */
    public static String withoutQueryString(String endpointPath) {
        if (endpointPath == null || endpointPath.isBlank()) {
            return "/";
        }
        String trimmed = endpointPath.trim();
        int queryIndex = trimmed.indexOf('?');
        return queryIndex >= 0 ? trimmed.substring(0, queryIndex) + "?<redacted>" : trimmed;
    }

    /**
     * 脱敏异常或诊断 message。
     *
     * @param message 原始 message；可能为空
     * @return 不含 credential-like value 的 message
     */
    public static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = message;
        for (String marker : SENSITIVE_MARKERS) {
            sanitized = sanitized.replaceAll("(?i)" + marker + "\\s*[=:]\\s*[^,;\\s]+", marker + "=<redacted>");
        }
        return sanitized;
    }

    /**
     * 判断字符串是否包含 credential-like marker。
     *
     * @param value 待检查内容
     * @return true 表示内容不应进入日志或 summary
     */
    public static boolean containsCredentialLikeMarker(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return SENSITIVE_MARKERS.stream().anyMatch(lower::contains);
    }
}
