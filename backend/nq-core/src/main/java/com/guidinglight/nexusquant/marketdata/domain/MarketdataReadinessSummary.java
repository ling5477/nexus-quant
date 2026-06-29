package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * MarketdataReadinessSummary is the stable read model returned by the GateM-2E readiness API.
 * <p>
 * Why: clients need one response that separates source health, freshness, gap and quality evidence
 * while preserving fail-closed states for no-data and unknown evidence.
 */
public record MarketdataReadinessSummary(
        String exchangeCode,
        String marketType,
        String instrumentId,
        String symbol,
        String interval,
        MarketdataReadinessStatus status,
        MarketdataReadinessStatus freshnessStatus,
        MarketdataReadinessStatus sourceHealthStatus,
        String sourceHealthReason,
        MarketdataQualityStatusSummary qualityStatusSummary,
        long barCount,
        Instant firstBarTime,
        Instant lastBarTime,
        Long expectedBarCount,
        Long gapCount,
        long unknownQualityCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        MarketdataBackendSupportLevel backendSupportLevel,
        Instant generatedAt
) {
    public MarketdataReadinessSummary {
        exchangeCode = requireText(exchangeCode, "exchangeCode");
        marketType = requireText(marketType, "marketType");
        instrumentId = requireText(instrumentId, "instrumentId");
        symbol = requireText(symbol, "symbol");
        interval = requireText(interval, "interval");
        status = Objects.requireNonNull(status, "status must not be null");
        freshnessStatus = Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
        sourceHealthStatus = Objects.requireNonNull(sourceHealthStatus, "sourceHealthStatus must not be null");
        sourceHealthReason = requireText(sourceHealthReason, "sourceHealthReason");
        qualityStatusSummary = Objects.requireNonNullElseGet(
                qualityStatusSummary,
                MarketdataQualityStatusSummary::empty
        );
        if (barCount < 0) {
            throw new IllegalArgumentException("barCount must not be negative");
        }
        if (expectedBarCount != null && expectedBarCount < 0) {
            throw new IllegalArgumentException("expectedBarCount must not be negative");
        }
        if (gapCount != null && gapCount < 0) {
            throw new IllegalArgumentException("gapCount must not be negative");
        }
        if (unknownQualityCount < 0) {
            throw new IllegalArgumentException("unknownQualityCount must not be negative");
        }
        backendSupportLevel = Objects.requireNonNull(backendSupportLevel, "backendSupportLevel must not be null");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
