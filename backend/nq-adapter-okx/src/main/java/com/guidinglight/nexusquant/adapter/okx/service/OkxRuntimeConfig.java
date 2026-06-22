package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
import com.guidinglight.nexusquant.common.runtime.ProcessEnvironmentResolver;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * OkxRuntimeConfig 负责统一解析 OKX 运行时环境变量。
 * <p>
 * Why:
 * 模拟盘/实盘验收要求按 `NQ_OKX_ENV` 在 `DOME/REAL` 两套凭证之间切换，并且严禁把 secret/passphrase 打到日志里。
 * 第十九批开始，启动脚本会先把 dome/real 专属变量归一化成统一运行时变量；这里优先读取统一变量，再回退到历史 DOME/REAL 命名，
 * 可以避免环境切换逻辑散落到 adapter 各处后失控。
 *
 * @param envName           当前环境名，仅允许 dome/real
 * @param baseUrl           当前环境的 OKX base URL
 * @param wsPrivateUrl      当前环境的 OKX 私有 WS URL
 * @param timeout           当前环境统一超时
 * @param instrumentRefresh instruments 刷新间隔
 * @param wsReconnectBase   WS 重连基础退避时长
 * @param wsReconnectMax    WS 重连最大退避时长
 * @param wsHeartbeat       WS 心跳周期
 * @param credentials       当前环境凭证
 * @param simulatedTrading  是否启用 OKX 模拟盘请求头
 */
public record OkxRuntimeConfig(
        String envName,
        String baseUrl,
        String wsPrivateUrl,
        Duration timeout,
        Duration instrumentRefresh,
        Duration wsReconnectBase,
        Duration wsReconnectMax,
        Duration wsHeartbeat,
        OkxApiCredentials credentials,
        boolean simulatedTrading
) {

    private static final String DEFAULT_ENV = "dome";
    // Why: 默认 endpoint 必须是 no-real sentinel，禁止把真实交易所 host 写成代码级默认值。
    // 真实 OKX endpoint 只能通过显式 env（NQ_OKX_BASE_URL / NQ_OKX_WS_URL 及 dome/real 前缀）opt-in。
    // disabled:// 在请求期 loud fail-closed（非法 scheme 抛 IllegalArgumentException），且 host 不含
    // 真实交易所域名，因此即使被误用也不会命中真实 OKX，也不会被 no-outbound denylist 误判。
    private static final String DEFAULT_BASE_URL = "disabled://okx-not-configured";
    private static final String DEFAULT_DOME_WS_PRIVATE_URL = "disabled://okx-ws-not-configured";
    private static final String DEFAULT_REAL_WS_PRIVATE_URL = "disabled://okx-ws-not-configured";
    private static final long DEFAULT_TIMEOUT_MS = 5_000L;
    private static final long DEFAULT_INSTRUMENT_REFRESH_MS = 300_000L;
    private static final long DEFAULT_WS_RECONNECT_BASE_DELAY_MS = 1_000L;
    private static final long DEFAULT_WS_RECONNECT_MAX_DELAY_MS = 30_000L;
    private static final long DEFAULT_WS_HEARTBEAT_INTERVAL_MS = 20_000L;

    /**
     * 从系统环境变量解析运行时配置。
     */
    public static OkxRuntimeConfig fromSystemEnv() {
        return fromEnvironment(ProcessEnvironmentResolver.resolveForCurrentProcess());
    }

    /**
     * 从指定环境变量集合解析运行时配置。
     * <p>
     * Why:
     * 单测需要稳定覆盖 dome/real 的选择逻辑，因此这里暴露显式 `Map` 入口，避免依赖不可控的进程环境。
     */
    public static OkxRuntimeConfig fromEnvironment(Map<String, String> env) {
        String rawEnv = read(env, "NQ_OKX_ENV", DEFAULT_ENV);
        String envName = normalizeEnv(rawEnv);
        boolean simulatedTrading = "dome".equals(envName);
        String prefix = simulatedTrading ? "NQ_OKX_DOME_" : "NQ_OKX_REAL_";
        String baseUrl = read(env, "NQ_OKX_BASE_URL", read(env, prefix + "BASE_URL", DEFAULT_BASE_URL));
        String defaultWsUrl = simulatedTrading ? DEFAULT_DOME_WS_PRIVATE_URL : DEFAULT_REAL_WS_PRIVATE_URL;
        String wsPrivateUrl = read(env, "NQ_OKX_WS_URL", read(env, prefix + "WS_URL", defaultWsUrl));
        long timeoutMs = readLong(env, "NQ_OKX_TIMEOUT_MS", readLong(env, "NQ_OKX_HTTP_TIMEOUT_MS", DEFAULT_TIMEOUT_MS));
        long refreshMs = readLong(env, "NQ_OKX_INSTRUMENT_REFRESH_MS", DEFAULT_INSTRUMENT_REFRESH_MS);
        long wsReconnectBaseDelayMs = readLong(
                env,
                "NQ_OKX_WS_RECONNECT_BASE_DELAY_MS",
                DEFAULT_WS_RECONNECT_BASE_DELAY_MS
        );
        long wsReconnectMaxDelayMs = readLong(
                env,
                "NQ_OKX_WS_RECONNECT_MAX_DELAY_MS",
                DEFAULT_WS_RECONNECT_MAX_DELAY_MS
        );
        long wsHeartbeatIntervalMs = readLong(
                env,
                "NQ_OKX_WS_HEARTBEAT_INTERVAL_MS",
                DEFAULT_WS_HEARTBEAT_INTERVAL_MS
        );
        OkxApiCredentials credentials = new OkxApiCredentials(
                read(env, "NQ_OKX_API_KEY", read(env, prefix + "API_KEY", "")),
                read(env, "NQ_OKX_API_SECRET", read(env, prefix + "API_SECRET", "")),
                read(env, "NQ_OKX_API_PASSPHRASE", read(env, prefix + "API_PASSPHRASE", ""))
        );
        return new OkxRuntimeConfig(
                envName,
                baseUrl,
                wsPrivateUrl,
                Duration.ofMillis(timeoutMs),
                Duration.ofMillis(refreshMs),
                Duration.ofMillis(wsReconnectBaseDelayMs),
                Duration.ofMillis(wsReconnectMaxDelayMs),
                Duration.ofMillis(wsHeartbeatIntervalMs),
                credentials,
                simulatedTrading
        );
    }

    /**
     * 生成可安全打印的连接指纹。
     * <p>
     * Why:
     * 验收需要定位当前连接的是哪套环境，但不能泄露 secret/passphrase，因此这里只暴露 env/baseUrl/apiKey 脱敏摘要。
     */
    public String fingerprint() {
        return "env=" + envName + ", baseUrl=" + baseUrl + ", apiKey=" + maskApiKey(credentials.apiKey());
    }

    private static String normalizeEnv(String value) {
        String normalized = value == null ? DEFAULT_ENV : value.trim().toLowerCase(Locale.ROOT);
        if ("demo".equals(normalized)) {
            // Why: 用户历史本地配置可能仍使用 demo；这里兼容读取，但对外统一按 docs 要求输出 dome。
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
