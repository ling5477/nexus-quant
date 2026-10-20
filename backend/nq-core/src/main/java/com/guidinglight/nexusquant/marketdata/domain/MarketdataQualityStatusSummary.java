package com.guidinglight.nexusquant.marketdata.domain;

import java.util.Map;

/**
 * MarketdataQualityStatusSummary aggregates bar-level quality_status values for one local DB scope.
 * <p>
 * Why: the readiness API must surface UNKNOWN/BAD/GAP evidence without leaking raw provider payloads
 * or treating abnormal quality statuses as ready.
 */
public record MarketdataQualityStatusSummary(
        long okCount,
        long gapSignalCount,
        long invalidCount,
        long unknownQualityCount,
        Map<String, Long> statuses
) {
    public MarketdataQualityStatusSummary {
        okCount = nonNegative(okCount, "okCount");
        gapSignalCount = nonNegative(gapSignalCount, "gapSignalCount");
        invalidCount = nonNegative(invalidCount, "invalidCount");
        unknownQualityCount = nonNegative(unknownQualityCount, "unknownQualityCount");
        statuses = statuses == null ? Map.of() : Map.copyOf(statuses);
    }

    public static MarketdataQualityStatusSummary empty() {
        return new MarketdataQualityStatusSummary(0, 0, 0, 0, Map.of());
    }

    private static long nonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
