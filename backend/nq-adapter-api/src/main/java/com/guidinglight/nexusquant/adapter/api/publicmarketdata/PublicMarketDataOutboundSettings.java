package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.time.Duration;
import java.util.Objects;

/**
 * PublicMarketDataOutboundSettings 固化 O-1 的 timeout / retry 安全默认值。
 *
 * <p>Why: public outbound 允许未来手动 profile 访问公开行情，但必须有 bounded timeout、bounded retry
 * 和 bounded backoff。默认值为 connect timeout 3s、read timeout 5s、total request timeout 8s、
 * maxRetries=2、backoff=500ms/1000ms；maxRetries 超过 2 会直接拒绝，避免无限重试。</p>
 *
 * @param connectTimeout      连接超时
 * @param readTimeout         读取超时语义；JDK client 用 total request timeout 执行，仍保留该配置供审计
 * @param totalRequestTimeout 单次 HTTP request 总超时
 * @param maxRetries          最大重试次数；O-1 不允许超过 2
 * @param firstBackoff        第一次 retry 前 backoff
 * @param secondBackoff       第二次 retry 前 backoff
 */
public record PublicMarketDataOutboundSettings(
        Duration connectTimeout,
        Duration readTimeout,
        Duration totalRequestTimeout,
        int maxRetries,
        Duration firstBackoff,
        Duration secondBackoff
) {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_TOTAL_REQUEST_TIMEOUT = Duration.ofSeconds(8);
    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final Duration DEFAULT_FIRST_BACKOFF = Duration.ofMillis(500);
    public static final Duration DEFAULT_SECOND_BACKOFF = Duration.ofMillis(1000);

    public PublicMarketDataOutboundSettings {
        connectTimeout = positive(connectTimeout, "connectTimeout");
        readTimeout = positive(readTimeout, "readTimeout");
        totalRequestTimeout = positive(totalRequestTimeout, "totalRequestTimeout");
        firstBackoff = nonNegative(firstBackoff, "firstBackoff");
        secondBackoff = nonNegative(secondBackoff, "secondBackoff");
        if (maxRetries < 0 || maxRetries > DEFAULT_MAX_RETRIES) {
            throw new IllegalArgumentException("maxRetries must be between 0 and 2");
        }
    }

    /**
     * @return O-1 默认 timeout / retry 配置。
     */
    public static PublicMarketDataOutboundSettings defaults() {
        return new PublicMarketDataOutboundSettings(
                DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_READ_TIMEOUT,
                DEFAULT_TOTAL_REQUEST_TIMEOUT,
                DEFAULT_MAX_RETRIES,
                DEFAULT_FIRST_BACKOFF,
                DEFAULT_SECOND_BACKOFF);
    }

    /**
     * 计算最大 attempts 数。
     *
     * @return 1 次初始请求 + maxRetries 次重试
     */
    public int maxAttempts() {
        return maxRetries + 1;
    }

    /**
     * 返回第 n 次 retry 前的 bounded backoff。
     *
     * @param retryNumber 从 1 开始
     * @return retry 1 使用 500ms，retry 2 使用 1000ms
     */
    public Duration backoffForRetry(int retryNumber) {
        return retryNumber <= 1 ? firstBackoff : secondBackoff;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration nonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
