package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * MarketdataReadinessResponse is the GateM-2E read-only readiness API payload.
 * <p>
 * Why: the response distinguishes NO_DATA/UNKNOWN/STALE/GAP from FRESH and carries the support level
 * so frontend clients cannot mistake no-migration MVP evidence for live exchange health.
 */
@Schema(name = "MarketdataReadinessResponse", description = "DB-only marketdata readiness summary")
public record MarketdataReadinessResponse(
        String exchangeCode,
        String marketType,
        String instrumentId,
        String symbol,
        String interval,
        String status,
        String freshnessStatus,
        String sourceHealthStatus,
        String sourceHealthReason,
        MarketdataQualityStatusSummaryResponse qualityStatusSummary,
        long barCount,
        Instant firstBarTime,
        Instant lastBarTime,
        Long expectedBarCount,
        Long gapCount,
        long unknownQualityCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String backendSupportLevel,
        Instant generatedAt
) {
    public static MarketdataReadinessResponse from(MarketdataReadinessSummary summary) {
        return new MarketdataReadinessResponse(
                summary.exchangeCode(),
                summary.marketType(),
                summary.instrumentId(),
                summary.symbol(),
                summary.interval(),
                summary.status().name(),
                summary.freshnessStatus().name(),
                summary.sourceHealthStatus().name(),
                summary.sourceHealthReason(),
                MarketdataQualityStatusSummaryResponse.from(summary.qualityStatusSummary()),
                summary.barCount(),
                summary.firstBarTime(),
                summary.lastBarTime(),
                summary.expectedBarCount(),
                summary.gapCount(),
                summary.unknownQualityCount(),
                summary.lastSuccessAt(),
                summary.lastFailureAt(),
                summary.backendSupportLevel().name(),
                summary.generatedAt()
        );
    }
}
