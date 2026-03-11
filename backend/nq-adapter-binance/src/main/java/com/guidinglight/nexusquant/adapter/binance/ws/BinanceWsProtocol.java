package com.guidinglight.nexusquant.adapter.binance.ws;

import com.guidinglight.nexusquant.adapter.binance.service.BinanceApiException;

/**
 * BinanceWsProtocol 负责 Binance 私有 WS 的最小协议辅助。
 * <p>
 * Why:
 * PR-BW1 只需要连接治理，不做业务映射；因此这里仅保留连接 URL 与指数退避等协议无关但容易出错的基础规则，
 * 避免在 client 内散落硬编码。
 */
final class BinanceWsProtocol {

    private static final String LEGACY_DOME_STREAM_URL = "wss://stream.testnet.binance.vision/ws";
    private static final String LEGACY_REAL_STREAM_URL = "wss://stream.binance.com:9443/ws";
    private static final String WS_API_DOME_URL = "wss://ws-api.testnet.binance.vision/ws-api/v3";
    private static final String WS_API_REAL_URL = "wss://ws-api.binance.com:443/ws-api/v3";

    private BinanceWsProtocol() {
    }

    static String buildUserDataStreamUrl(String wsBaseUrl, String listenKey) {
        if (wsBaseUrl == null || wsBaseUrl.isBlank()) {
            throw new IllegalArgumentException("wsBaseUrl must not be blank");
        }
        if (listenKey == null || listenKey.isBlank()) {
            throw new IllegalArgumentException("listenKey must not be blank");
        }
        String normalizedBaseUrl = wsBaseUrl.endsWith("/") ? wsBaseUrl.substring(0, wsBaseUrl.length() - 1) : wsBaseUrl;
        return normalizedBaseUrl + "/" + listenKey;
    }

    static long reconnectDelayMs(int attempt, long baseDelayMs, long maxDelayMs) {
        if (attempt <= 0) {
            return baseDelayMs;
        }
        long candidate;
        if (attempt >= 31) {
            candidate = maxDelayMs;
        } else {
            candidate = baseDelayMs * (1L << (attempt - 1));
        }
        return Math.min(candidate, maxDelayMs);
    }

    /**
     * 解析当前配置应使用的用户数据流 WebSocket API 地址。
     * <p>
     * Why:
     * Binance 已把用户数据流迁移到 `ws-api` 订阅模型；仓库里仍可能残留旧的 `/ws/<listenKey>` 配置。
     * 这里统一做兼容转换，避免本地 `.env` 仍指向旧 stream 域名时继续命中 410 Gone。
     */
    static String resolveUserDataWsApiUrl(String configuredWsUrl, String envName) {
        if (configuredWsUrl == null || configuredWsUrl.isBlank()) {
            return "real".equalsIgnoreCase(envName) ? WS_API_REAL_URL : WS_API_DOME_URL;
        }
        String normalized = configuredWsUrl.endsWith("/")
                ? configuredWsUrl.substring(0, configuredWsUrl.length() - 1)
                : configuredWsUrl;
        if (normalized.contains("/ws-api/")) {
            return normalized;
        }
        if (normalized.startsWith(LEGACY_DOME_STREAM_URL)) {
            return WS_API_DOME_URL;
        }
        if (normalized.startsWith(LEGACY_REAL_STREAM_URL) || normalized.startsWith("wss://stream.binance.com/ws")) {
            return WS_API_REAL_URL;
        }
        return normalized;
    }

    /**
     * 判断当前配置是否已经显式使用 ws-api 模型。
     */
    static boolean isWsApiUrl(String wsUrl) {
        return wsUrl != null && wsUrl.contains("/ws-api/");
    }

    /**
     * 判断 listenKey 失败是否属于“官方端点已退役”的场景。
     * <p>
     * Why:
     * GateC 当前唯一阻塞点是 `/api/v3/userDataStream -> 410 Gone`。
     * 命中该错误时应切换到官方 `ws-api` 订阅模型，而不是持续盲目重试旧端点。
     */
    static boolean shouldFallbackToWsApi(BinanceApiException exception) {
        return exception != null && exception.httpStatus() == 410;
    }
}
