package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * MarketdataReadinessResponse 是行情就绪聚合的只读 API 响应。
 * <p>
 * 原因：响应保留既有行情就绪字段，并追加与 DataQualitySummary 对齐的
 * diagnostics. exchange/exchangeCode and timeframe/interval are compatibility aliases; none of these
 * fields represent real provider readiness, private trading readiness, LIVE readiness or permission grants.
 */
@Schema(name = "MarketdataReadinessResponse", description = "Read-only marketdata readiness summary")
public record MarketdataReadinessResponse(
        String exchangeCode,
        String exchange,
        String marketType,
        String instrumentId,
        String symbol,
        String interval,
        String timeframe,
        String sourceCode,
        String dataOrigin,
        String status,
        String sourceStatus,
        String freshnessStatus,
        String sourceHealthStatus,
        String sourceHealth,
        String sourceHealthReason,
        String gapStatus,
        MarketdataQualityStatusSummaryResponse qualityStatusSummary,
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
        String errorCategory,
        Long staleAfterSeconds,
        String degradedReason,
        String disabledReason,
        String traceId,
        String requestId,
        String backendSupportLevel,
        Instant generatedAt,
        Instant updatedAt
) {
    public static MarketdataReadinessResponse from(MarketdataReadinessSummary summary) {
        return new MarketdataReadinessResponse(
                summary.exchangeCode(),
                summary.exchange(),
                summary.marketType(),
                summary.instrumentId(),
                summary.symbol(),
                summary.interval(),
                summary.timeframe(),
                summary.sourceCode(),
                summary.dataOrigin().name(),
                summary.status().name(),
                summary.sourceStatus().name(),
                summary.freshnessStatus().name(),
                summary.sourceHealthStatus().name(),
                summary.sourceHealth().name(),
                summary.sourceHealthReason(),
                summary.gapStatus().name(),
                MarketdataQualityStatusSummaryResponse.from(summary.qualityStatusSummary()),
                summary.barCount(),
                summary.firstBarTime(),
                summary.lastBarTime(),
                summary.expectedBarCount(),
                summary.gapCount(),
                summary.missingFrom(),
                summary.missingTo(),
                summary.unknownQualityCount(),
                summary.lastSuccessAt(),
                summary.lastFailureAt(),
                summary.lastObservedAt(),
                summary.latencyMs(),
                summary.errorRate(),
                summary.errorCategory().name(),
                summary.staleAfterSeconds(),
                summary.degradedReason(),
                summary.disabledReason(),
                summary.traceId(),
                summary.requestId(),
                summary.backendSupportLevel().name(),
                summary.generatedAt(),
                summary.updatedAt()
        );
    }
}
