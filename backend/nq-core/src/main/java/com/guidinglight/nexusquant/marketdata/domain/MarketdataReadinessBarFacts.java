package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * MarketdataReadinessBarFacts contains bounded aggregate facts read from marketdata_bars.
 * <p>
 * Why: GateM-2E readiness must be computed from local database facts only, keeping JDBC details in
 * infra while allowing application tests to exercise the status resolver without a database.
 */
public record MarketdataReadinessBarFacts(
        long barCount,
        Instant firstOpenTime,
        Instant lastOpenTime,
        Instant lastCloseTime,
        MarketdataQualityStatusSummary qualityStatusSummary
) {
    public MarketdataReadinessBarFacts {
        if (barCount < 0) {
            throw new IllegalArgumentException("barCount must not be negative");
        }
        qualityStatusSummary = Objects.requireNonNullElseGet(
                qualityStatusSummary,
                MarketdataQualityStatusSummary::empty
        );
    }

    public static MarketdataReadinessBarFacts empty() {
        return new MarketdataReadinessBarFacts(0, null, null, null, MarketdataQualityStatusSummary.empty());
    }
}
