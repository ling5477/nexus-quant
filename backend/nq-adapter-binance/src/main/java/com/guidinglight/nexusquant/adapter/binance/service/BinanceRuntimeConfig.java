package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.common.runtime.ProcessEnvironmentResolver;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * BinanceRuntimeConfig 统一解析 Binance 运行时环境变量。
 * <p>
 * Why:
 * GateC-2 仍要求沿用 `NQ_*_ENV` 双环境切换与指纹脱敏策略。
 * 这里把 env 选择、超时、签名时间偏移、exchangeInfo 刷新窗口与凭证读取收敛到单点，
 * 避免后续 TradingAdapter / cache / ws client 各自散落读取环境变量。
 *
 * @param envName                     当前环境名，仅允许 dome/real
 * @param baseUrl                     当前环境 Binance REST base URL；默认 no-real sentinel `disabled://binance-not-configured`，真实 endpoint 仅显式 env opt-in
 * @param wsUrl                       当前环境 Binance 私有 WS base URL；默认 no-real sentinel `disabled://binance-ws-not-configured`，真实 endpoint 仅显式 env opt-in
 * @param timeout                     单次请求超时
 * @param signedTimestampOffset       在 serverTime 校准前额外叠加的人工偏移，便于 local/debug 定向排障
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
        Duration signedTimestampOffset,
        Duration exchangeInfoRefreshInterval,
        Duration wsReconnectBase,
        Duration wsReconnectMax,
        Duration wsHeartbeat,
        Duration listenKeyRefreshInterval,
        boolean wsDiagnosticEnabled,
        BinanceApiCredentials credentials
) {

    private static final String DEFAULT_ENV = "dome";
    // Why: No-real hardening (GateL-1B-A) —— 默认 endpoint 必须是 no-real sentinel，禁止把 testnet/mainnet
    // host 写成代码级默认值。真实 Binance endpoint 只能通过显式 env（NQ_BINANCE_<DOME|REAL>_BASE_URL /
    // _WS_URL）opt-in；未配置一律 fail-closed。disabled:// 在请求期 loud fail-closed：REST 经
    // HttpRequest.Builder.uri()、WS 经 WebSocket.Builder.buildAsync() 对非 http(s)/ws(s) scheme 抛
    // IllegalArgumentException，且 host 不含真实交易所域名，即使被误用也不会命中 testnet/mainnet，
    // 也不会被 no-outbound denylist 误判。dome/real 共用同一 sentinel：环境选择不得隐含真实 endpoint。
    public static final String DEFAULT_BASE_URL = "disabled://binance-not-configured";
    public static final String DEFAULT_WS_URL = "disabled://binance-ws-not-configured";
    private static final long DEFAULT_TIMEOUT_MS = 3_000L;
    private static final long DEFAULT_SIGNED_TIMESTAMP_OFFSET_MS = 0L;
    private static final long DEFAULT_EXCHANGE_INFO_REFRESH_MS = 300_000L;
    private static final long DEFAULT_WS_RECONNECT_BASE_DELAY_MS = 1_000L;
    private static final long DEFAULT_WS_RECONNECT_MAX_DELAY_MS = 30_000L;
    private static final long DEFAULT_WS_HEARTBEAT_INTERVAL_MS = 20_000L;
    private static final long DEFAULT_LISTENKEY_REFRESH_MS = 30 * 60 * 1_000L;

    /**
     * 从系统环境变量构建运行时配置。
     */
    public static BinanceRuntimeConfig fromSystemEnv() {
        return fromEnvironment(ProcessEnvironmentResolver.resolveForCurrentProcess());
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
        return new BinanceRuntimeConfig(
                envName,
                read(env, prefix + "BASE_URL", DEFAULT_BASE_URL),
                normalizeWsUrl(read(env, prefix + "WS_URL", DEFAULT_WS_URL)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_TIMEOUT_MS", DEFAULT_TIMEOUT_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_SIGNED_TIMESTAMP_OFFSET_MS", DEFAULT_SIGNED_TIMESTAMP_OFFSET_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_EXCHANGE_INFO_REFRESH_MS", DEFAULT_EXCHANGE_INFO_REFRESH_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_WS_RECONNECT_BASE_DELAY_MS", DEFAULT_WS_RECONNECT_BASE_DELAY_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_WS_RECONNECT_MAX_DELAY_MS", DEFAULT_WS_RECONNECT_MAX_DELAY_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_WS_HEARTBEAT_INTERVAL_MS", DEFAULT_WS_HEARTBEAT_INTERVAL_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_LISTENKEY_REFRESH_MS", DEFAULT_LISTENKEY_REFRESH_MS)),
                Boolean.parseBoolean(read(env, "NQ_BINANCE_WS_DIAGNOSTIC_ENABLED", "false")),
                // Why: No-real hardening (GateL-1B-B) —— runtime config 不再从进程环境（env / system property / .env）
                // 读取 credential material（apiKey/secret/private key/key type）。默认一律 unconfigured placeholder；
                // 真实 credential 必须由后续 NQ credential governance bridge 按 owner/account/tenant/credential type/
                // active version/permission scope 注入（另起 Gate），adapter 不得从全局进程环境派生。未配置时
                // BinanceHttpClient 对 signed 请求在网络前 fail-closed（BINANCE_CREDENTIALS_MISSING）。
                BinanceApiCredentials.unconfigured()
        );
    }

    /**
     * 生成可安全输出的连接指纹。
     */
    public String fingerprint() {
        return "env=" + envName
                + ", baseUrl=" + baseUrl
                + ", wsUrl=" + wsUrl
                + ", signedTimestampOffsetMs=" + signedTimestampOffset.toMillis()
                + ", apiKey=" + maskApiKey(credentials.apiKey());
    }

    /**
     * Why:
     * Binance dome / real 在本地环境里可能因为宿主机轻微时钟漂移触发 `-1021`。
     * 该方法只负责把人工偏移统一收口，真正的 serverTime 校准由 timestamp provider 完成，
     * 这样 REST / ws-api 至少共享同一个显式配置口径。
     *
     * @param currentEpochMillis 当前本地时间戳
     * @return 叠加人工偏移后的时间戳
     */
    public long signedEpochMillis(long currentEpochMillis) {
        return currentEpochMillis + signedTimestampOffset.toMillis();
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
     * No-real hardening (GateL-1B-A)：WS 默认 / 空值必须 fail-closed 到 no-real sentinel，
     * 禁止在 blank/legacy URL 情况下回退到 testnet/mainnet。显式配置按原样使用（仅去除尾部 `/`），
     * 真实 ws-api endpoint 只能由显式 env opt-in，不再由代码把 legacy `stream.../ws` host
     * 静默改写成真实 ws-api host（旧改写会构造真实网络 endpoint，违反 No-Real 边界）。
     */
    private static String normalizeWsUrl(String configuredWsUrl) {
        if (configuredWsUrl == null || configuredWsUrl.isBlank()) {
            return DEFAULT_WS_URL;
        }
        String normalized = configuredWsUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
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
