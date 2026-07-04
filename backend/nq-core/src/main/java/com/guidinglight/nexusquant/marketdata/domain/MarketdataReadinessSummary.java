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
        String exchange,
        String marketType,
        String instrumentId,
        String symbol,
        String interval,
        String timeframe,
        String sourceCode,
        MarketdataReadinessDataOrigin dataOrigin,
        MarketdataReadinessStatus status,
        MarketdataReadinessSourceStatus sourceStatus,
        MarketdataReadinessStatus freshnessStatus,
        MarketdataReadinessStatus sourceHealthStatus,
        MarketdataReadinessSourceHealth sourceHealth,
        String sourceHealthReason,
        MarketdataReadinessGapStatus gapStatus,
        MarketdataQualityStatusSummary qualityStatusSummary,
        long barCount,
        Instant firstBarTime,
        Instant lastBarTime,
        Long expectedBarCount,
        Long gapCount,
        Instant missingFrom,
        Instant missingTo,
        long unknownQualityCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        Instant lastObservedAt,
        Long latencyMs,
        Double errorRate,
        MarketdataReadinessErrorCategory errorCategory,
        Long staleAfterSeconds,
        String degradedReason,
        String disabledReason,
        String traceId,
        String requestId,
        MarketdataBackendSupportLevel backendSupportLevel,
        Instant generatedAt,
        Instant updatedAt
) {
    public MarketdataReadinessSummary {
        exchangeCode = requireText(exchangeCode, "exchangeCode");
        exchange = requireText(exchange, "exchange");
        marketType = requireText(marketType, "marketType");
        instrumentId = requireText(instrumentId, "instrumentId");
        symbol = requireText(symbol, "symbol");
        interval = requireText(interval, "interval");
        timeframe = requireText(timeframe, "timeframe");
        sourceCode = requireText(sourceCode, "sourceCode");
        dataOrigin = Objects.requireNonNull(dataOrigin, "dataOrigin must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        sourceStatus = Objects.requireNonNull(sourceStatus, "sourceStatus must not be null");
        freshnessStatus = Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
        sourceHealthStatus = Objects.requireNonNull(sourceHealthStatus, "sourceHealthStatus must not be null");
        sourceHealth = Objects.requireNonNull(sourceHealth, "sourceHealth must not be null");
        sourceHealthReason = requireText(sourceHealthReason, "sourceHealthReason");
        gapStatus = Objects.requireNonNull(gapStatus, "gapStatus must not be null");
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
        if (latencyMs != null && latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        if (errorRate != null && (errorRate < 0 || errorRate > 1)) {
            throw new IllegalArgumentException("errorRate must be between 0 and 1");
        }
        errorCategory = Objects.requireNonNull(errorCategory, "errorCategory must not be null");
        if (staleAfterSeconds != null && staleAfterSeconds < 0) {
            throw new IllegalArgumentException("staleAfterSeconds must not be negative");
        }
        if (unknownQualityCount < 0) {
            throw new IllegalArgumentException("unknownQualityCount must not be negative");
        }
        backendSupportLevel = Objects.requireNonNull(backendSupportLevel, "backendSupportLevel must not be null");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        degradedReason = normalizeNullableText(degradedReason);
        disabledReason = normalizeNullableText(disabledReason);
        traceId = normalizeNullableText(traceId);
        requestId = normalizeNullableText(requestId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
