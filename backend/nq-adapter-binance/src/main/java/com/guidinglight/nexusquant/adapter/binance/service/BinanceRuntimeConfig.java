package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;

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
 * @param timeout                     单次请求超时
 * @param exchangeInfoRefreshInterval exchangeInfo cache 刷新间隔
 * @param credentials                 当前环境凭证
 */
public record BinanceRuntimeConfig(
        String envName,
        String baseUrl,
        Duration timeout,
        Duration exchangeInfoRefreshInterval,
        BinanceApiCredentials credentials
) {

    private static final String DEFAULT_ENV = "dome";
    private static final String DEFAULT_DOME_BASE_URL = "https://testnet.binance.vision";
    private static final String DEFAULT_REAL_BASE_URL = "https://api.binance.com";
    private static final long DEFAULT_TIMEOUT_MS = 3_000L;
    private static final long DEFAULT_EXCHANGE_INFO_REFRESH_MS = 300_000L;

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
        return new BinanceRuntimeConfig(
                envName,
                read(env, prefix + "BASE_URL", defaultBaseUrl),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_TIMEOUT_MS", DEFAULT_TIMEOUT_MS)),
                Duration.ofMillis(readLong(env, "NQ_BINANCE_EXCHANGE_INFO_REFRESH_MS", DEFAULT_EXCHANGE_INFO_REFRESH_MS)),
                new BinanceApiCredentials(
                        read(env, prefix + "API_KEY", ""),
                        read(env, prefix + "API_SECRET", "")
                )
        );
    }

    /**
     * 生成可安全输出的连接指纹。
     */
    public String fingerprint() {
        return "env=" + envName + ", baseUrl=" + baseUrl + ", apiKey=" + maskApiKey(credentials.apiKey());
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
