package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * MarketdataQualityBarScopeFacts 是按 exchange / market / symbol / interval / source 聚合的本地 bar 事实。
 * <p>
 * Why:
 * overview 需要支持多 symbol / 多 interval 聚合；按 scope 保留 first/last 时间和质量分布，才能在 service
 * 层稳定计算 expected bars、gap 和 stale count，同时让 infra 只负责 SQL 读取。
 */
public record MarketdataQualityBarScopeFacts(
        String exchangeCode,
        String marketType,
        String symbol,
        BarInterval interval,
        String source,
        long barCount,
        Instant firstOpenTime,
        Instant lastOpenTime,
        Instant lastCloseTime,
        MarketdataQualityStatusSummary qualityStatusSummary
) {
    public MarketdataQualityBarScopeFacts {
        exchangeCode = requireText(exchangeCode, "exchangeCode");
        marketType = requireText(marketType, "marketType");
        symbol = requireText(symbol, "symbol");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        source = normalizeNullable(source);
        if (barCount < 0) {
            throw new IllegalArgumentException("barCount must not be negative");
        }
        qualityStatusSummary = Objects.requireNonNullElseGet(
                qualityStatusSummary,
                MarketdataQualityStatusSummary::empty
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
