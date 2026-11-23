package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;

/**
 * MarketdataReadinessIngestionFacts contains sanitized ingestion job/run timestamps for one scope.
 * <p>
 * Why: readiness can explain recent local ingestion success or failure, but it must not expose raw
 * error payloads, provider responses, credentials, headers, signatures, or stack traces.
 */
public record MarketdataReadinessIngestionFacts(
        Instant lastSuccessAt,
        Instant lastFailureAt,
        Long latestLatencyMs,
        String latestRunStatus
) {
    public MarketdataReadinessIngestionFacts {
        if (latestLatencyMs != null && latestLatencyMs < 0) {
            throw new IllegalArgumentException("latestLatencyMs must not be negative");
        }
    }

    public static MarketdataReadinessIngestionFacts empty() {
        return new MarketdataReadinessIngestionFacts(null, null, null, null);
    }
}
