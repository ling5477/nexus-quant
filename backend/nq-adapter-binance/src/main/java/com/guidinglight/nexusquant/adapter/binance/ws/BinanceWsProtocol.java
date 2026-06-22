package com.guidinglight.nexusquant.adapter.binance.ws;

import com.guidinglight.nexusquant.adapter.binance.service.BinanceApiException;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceRuntimeConfig;

/**
 * BinanceWsProtocol 负责 Binance 私有 WS 的最小协议辅助。
 * <p>
 * Why:
 * PR-BW1 只需要连接治理，不做业务映射；因此这里仅保留连接 URL 与指数退避等协议无关但容易出错的基础规则，
 * 避免在 client 内散落硬编码。
 */
final class BinanceWsProtocol {

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
     * No-real hardening (GateL-1B-A)：这是 WS 连接路径实际使用的 endpoint 解析点。
     * blank/missing WS URL 必须 fail-closed 到 no-real sentinel（`BinanceRuntimeConfig.DEFAULT_WS_URL`），
     * 禁止把 testnet/mainnet ws-api host 当成默认或回退；真实 ws-api endpoint 只能由显式 env opt-in。
     * 旧实现会把 legacy `stream.../ws` host 静默改写成真实 ws-api host，会在 guard 关闭时构造真实网络
     * URI，违反 No-Real 边界，因此移除该改写：显式配置按原样使用（仅去除尾部 `/`）。
     */
    static String resolveUserDataWsApiUrl(String configuredWsUrl) {
        if (configuredWsUrl == null || configuredWsUrl.isBlank()) {
            return BinanceRuntimeConfig.DEFAULT_WS_URL;
        }
        return configuredWsUrl.endsWith("/")
                ? configuredWsUrl.substring(0, configuredWsUrl.length() - 1)
                : configuredWsUrl;
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
