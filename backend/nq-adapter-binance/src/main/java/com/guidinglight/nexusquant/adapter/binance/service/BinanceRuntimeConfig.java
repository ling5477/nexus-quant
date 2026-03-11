package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * BinanceRuntimeConfig 统一解析 Binance 运行时环境变量。
 * <p>
 * Why:
 * GateC-2 仍要求沿用 `NQ_*_ENV` 双环境切换与指纹脱敏策略。
 * 这里把 env 选择、超时、exchangeInfo 刷新窗口与凭证读取收敛到单点，避免后续 TradingAdapter/cache 各自散落读取环境变量。
 *
 * @param envName                     当前环境名，仅允许 dome/real
 * @param baseUrl                     当前环境 Binance REST base URL
 * @param wsUrl                       当前环境 Binance 私有 WS base URL；默认使用 ws-api 用户流订阅地址
 * @param timeout                     单次请求超时
 * @param exchangeInfoRefreshInterval exchangeInfo cache 刷新间隔
 * @param wsReconnectBase             WS 重连基础退避时长
 * @param wsReconnectMax              WS 重连最大退避时长
 * @param wsHeartbeat                 WS 心跳巡检周期
 * @param listenKeyRefreshInterval    listenKey keepalive 周期
 * @param wsDiagnosticEnabled         是否输出本地诊断日志（仅用于 local/debug 排障）
 * @param credentials                 当前环境凭证
 */
public record BinanceRuntimeConfig(
        String envName,
        String baseUrl,
        String wsUrl,
        Duration timeout,
        Duration exchangeInfoRefreshInterval,
        Duration wsReconnectBase,
        Duration wsReconnectMax,
        Duration wsHeartbeat,
        Duration listenKeyRefreshInterval,
        boolean wsDiagnosticEnabled,
        BinanceApiCredentials credentials
) {

    private static final String DEFAULT_ENV = "dome";
    private static final String DEFAULT_DOME_BASE_URL = "https://testnet.binance.vision";
    private static final String DEFAULT_REAL_BASE_URL = "https://api.binance.com";
    private static final String DEFAULT_DOME_WS_URL = "wss://ws-api.testnet.binance.vision/ws-api/v3";
    private static final String DEFAULT_REAL_WS_URL = "wss://ws-api.binance.com:443/ws-api/v3";
    private static final long DEFAULT_TIMEOUT_MS = 3_000L;
    private static final long DEFAULT_EXCHANGE_INFO_REFRESH_MS = 300_000L;
    private static final long DEFAULT_WS_RECONNECT_BASE_DELAY_MS = 1_000L;
    private static final long DEFAULT_WS_RECONNECT_MAX_DELAY_MS = 30_000L;
    private static final long DEFAULT_WS_HEARTBEAT_INTERVAL_MS = 20_000L;
    private static final long DEFAULT_LISTENKEY_REFRESH_MS = 30 * 60 * 1_000L;

    /**
     * 从系统环境变量构建运行时配置。
     */
    public static BinanceRuntimeConfig fromSystemEnv() {
        return fromEnvironment(System.getenv());
    }

    /**
     * 从指定环境变量集合构建运行时配置。
     * <p>
     * Why:
     * 单测要稳定覆盖 dome/real 切换、指纹脱敏与刷新窗口，因此不能直接依赖进程环境。
     */
    public static BinanceRuntimeConfig fromEnvironment(Map<String, String> env) {
        String envName = normalizeEnv(read(env, "NQ_BINANCE_ENV", DEFAULT_ENV));
        boolean dome = "dome".equals(envName);
        String prefix = dome ? "NQ_BINANCE_DOME_" : "NQ_BINANCE_REAL_";
        String defaultBaseUrl = dome ? DEFAULT_DOME_BASE_URL : DEFAULT_REAL_BASE_URL;
        String defaultWsUrl = dome ? DEFAULT_DOME_WS_URL : DEFAULT_REAL_WS_URL;
        return new BinanceRuntimeConfig(
                envName,
                read(env, prefix + "BASE_URL", defaultBaseUrl),
                normalizeWsUrl(read(env, prefix + "WS_URL", defaultWsUrl), envName),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_TIMEOUT_MS", DEFAULT_TIMEOUT_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_EXCHANGE_INFO_REFRESH_MS", DEFAULT_EXCHANGE_INFO_REFRESH_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_WS_RECONNECT_BASE_DELAY_MS", DEFAULT_WS_RECONNECT_BASE_DELAY_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_WS_RECONNECT_MAX_DELAY_MS", DEFAULT_WS_RECONNECT_MAX_DELAY_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_WS_HEARTBEAT_INTERVAL_MS", DEFAULT_WS_HEARTBEAT_INTERVAL_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_LISTENKEY_REFRESH_MS", DEFAULT_LISTENKEY_REFRESH_MS)),
                Boolean.parseBoolean(read(env, "NQ_BINANCE_WS_DIAGNOSTIC_ENABLED", "false")),
                new BinanceApiCredentials(
                        read(env, prefix + "API_KEY", ""),
                        read(env, prefix + "API_SECRET", ""),
                        BinanceKeyType.fromEnv(read(env, "NQ_BINANCE_KEY_TYPE", "hmac")),
                        read(env, prefix + "PRIVATE_KEY", ""),
                        read(env, prefix + "PRIVATE_KEY_PATH", "")
                )
        );
    }

    /**
     * 生成可安全输出的连接指纹。
     */
    public String fingerprint() {
        return "env=" + envName
                + ", baseUrl=" + baseUrl
                + ", wsUrl=" + wsUrl
                + ", apiKey=" + maskApiKey(credentials.apiKey());
    }

    private static String normalizeEnv(String value) {
        String normalized = value == null ? DEFAULT_ENV : value.trim().toLowerCase(Locale.ROOT);
        if ("demo".equals(normalized)) {
            return "dome";
        }
        return "real".equals(normalized) ? "real" : "dome";
    }

    private static String read(Map<String, String> env, String key, String defaultValue) {
        String value = env.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static long readLong(Map<String, String> env, String key, long defaultValue) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    /**
     * Why:
     * GateC-2.1 当前只允许 Binance 用户数据流走官方 `ws-api` 入口。
     * 本地 `.env` 可能仍残留早期 `stream.../ws` 地址；如果这里不在配置解析阶段统一归一化，
     * 指纹日志、诊断输出和实际连接目标就会分裂，继续污染 BW4/BW5 的排障结论。
     */
    private static String normalizeWsUrl(String configuredWsUrl, String envName) {
        if (configuredWsUrl == null || configuredWsUrl.isBlank()) {
            return "real".equalsIgnoreCase(envName) ? DEFAULT_REAL_WS_URL : DEFAULT_DOME_WS_URL;
        }
        String normalized = configuredWsUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("/ws-api/")) {
            return normalized;
        }
        if (normalized.startsWith("wss://stream.testnet.binance.vision/ws")) {
            return DEFAULT_DOME_WS_URL;
        }
        if (normalized.startsWith("wss://stream.binance.com:9443/ws")
                || normalized.startsWith("wss://stream.binance.com/ws")) {
            return DEFAULT_REAL_WS_URL;
        }
        return normalized;
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "missing";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return trimmed.charAt(0) + "***" + trimmed.charAt(trimmed.length() - 1);
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }
}
