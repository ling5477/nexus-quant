package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * PublicMarketDataOutboundResult 是 public outbound client 的脱敏返回模型。
 *
 * <p>Why: O-1 只需要证明边界、错误分类、retry、fallback 和 Data Quality linkage；返回模型不得携带
 * raw request body、raw response body、raw headers、full query string、signature、token 或 credential
 * material。因此本 record 只保留允许日志字段和可审计状态。</p>
 *
 * @param exchange         exchange/source 名称；用于诊断，不授权 trading
 * @param endpointCategory endpoint 类别
 * @param errorCategory    出站结果分类
 * @param statusCode       HTTP status；无 HTTP 响应时为 0
 * @param latency          本次调用总耗时
 * @param attempts         已尝试次数；policy denied/disabled 可为 0
 * @param dataOrigin       数据来源
 * @param rowCount         解析出的最小行数；未知为 0
 * @param stale            是否判定 stale
 * @param gapCount         缺口数；无缺口为 0
 * @param fallbackUsed     是否使用 fallback 来源
 * @param checkedAt        结果生成时间
 * @param message          脱敏说明；不得包含 raw provider material
 */
public record PublicMarketDataOutboundResult(
        String exchange,
        PublicMarketDataEndpointCategory endpointCategory,
        PublicMarketDataOutboundErrorCategory errorCategory,
        int statusCode,
        Duration latency,
        int attempts,
        PublicMarketDataQualitySummary.DataOrigin dataOrigin,
        int rowCount,
        boolean stale,
        int gapCount,
        boolean fallbackUsed,
        Instant checkedAt,
        String message
) {

    public PublicMarketDataOutboundResult {
        exchange = normalize(exchange);
        endpointCategory = Objects.requireNonNull(endpointCategory, "endpointCategory must not be null");
        errorCategory = Objects.requireNonNull(errorCategory, "errorCategory must not be null");
        latency = latency == null ? Duration.ZERO : latency;
        dataOrigin = Objects.requireNonNull(dataOrigin, "dataOrigin must not be null");
        checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        message = PublicMarketDataRedactor.sanitizeMessage(message);
        if (statusCode < 0) {
            throw new IllegalArgumentException("statusCode must not be negative");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        if (rowCount < 0) {
            throw new IllegalArgumentException("rowCount must not be negative");
        }
        if (gapCount < 0) {
            throw new IllegalArgumentException("gapCount must not be negative");
        }
    }

    /**
     * 构造 policy denied 结果。
     *
     * @param request  触发拒绝的请求
     * @param decision policy 决策
     * @return attempts=0 的脱敏结果
     */
    public static PublicMarketDataOutboundResult denied(
            PublicMarketDataOutboundRequest request, PublicMarketDataOutboundDecision decision) {
        return new PublicMarketDataOutboundResult(
                request.exchange(),
                decision.endpointCategory(),
                decision.errorCategory(),
                0,
                Duration.ZERO,
                0,
                PublicMarketDataQualitySummary.DataOrigin.LOCAL_DB,
                0,
                false,
                0,
                true,
                decision.checkedAt(),
                decision.reason());
    }

    /**
     * 构造 disabled/fallback 结果。
     *
     * @param request        请求上下文
     * @param fallbackOrigin fallback 来源
     * @param checkedAt      结果时间
     * @return DISABLED 分类结果
     */
    public static PublicMarketDataOutboundResult disabled(
            PublicMarketDataOutboundRequest request,
            PublicMarketDataQualitySummary.DataOrigin fallbackOrigin,
            Instant checkedAt
    ) {
        return new PublicMarketDataOutboundResult(
                request.exchange(),
                request.endpointCategory(),
                PublicMarketDataOutboundErrorCategory.DISABLED,
                0,
                Duration.ZERO,
                0,
                fallbackOrigin,
                0,
                false,
                0,
                true,
                checkedAt,
                "public marketdata outbound disabled; fallback origin selected");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim();
    }
}
