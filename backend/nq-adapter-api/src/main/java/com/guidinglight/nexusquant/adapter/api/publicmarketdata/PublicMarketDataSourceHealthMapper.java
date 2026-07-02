package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.time.Duration;
import java.util.Objects;

/**
 * PublicMarketDataSourceHealthMapper 把 O-1 outbound result 映射到 O-2 Data Quality 语义。
 *
 * <p>Why: outbound success 只表示一次公开请求可读；Data Quality 还必须单独表达 latency、rate limit、
 * timeout、5xx、stale、gap、disabled 和 fallback。该 mapper 永远返回 tradingAuthorization=false。</p>
 */
public final class PublicMarketDataSourceHealthMapper {

    private static final Duration HIGH_LATENCY_THRESHOLD = Duration.ofSeconds(2);

    private PublicMarketDataSourceHealthMapper() {
    }

    /**
     * 映射出站结果为最小 Data Quality summary。
     *
     * @param result 出站结果；不可为空
     * @return source health / freshness / gap / source status / origin summary
     */
    public static PublicMarketDataQualitySummary map(PublicMarketDataOutboundResult result) {
        Objects.requireNonNull(result, "result must not be null");
        PublicMarketDataQualitySummary.SourceStatus sourceStatus =
                result.errorCategory() == PublicMarketDataOutboundErrorCategory.DISABLED
                        ? PublicMarketDataQualitySummary.SourceStatus.DISABLED
                        : PublicMarketDataQualitySummary.SourceStatus.ENABLED;
        PublicMarketDataQualitySummary.SourceHealth sourceHealth = sourceHealth(result);
        PublicMarketDataQualitySummary.Freshness freshness = result.stale()
                ? PublicMarketDataQualitySummary.Freshness.STALE
                : PublicMarketDataQualitySummary.Freshness.FRESH;
        return new PublicMarketDataQualitySummary(
                sourceHealth,
                freshness,
                result.gapCount(),
                sourceStatus,
                result.dataOrigin(),
                result.fallbackUsed(),
                false);
    }

    private static PublicMarketDataQualitySummary.SourceHealth sourceHealth(
            PublicMarketDataOutboundResult result) {
        return switch (result.errorCategory()) {
            case NONE -> result.latency().compareTo(HIGH_LATENCY_THRESHOLD) > 0
                    ? PublicMarketDataQualitySummary.SourceHealth.DEGRADED
                    : PublicMarketDataQualitySummary.SourceHealth.HEALTHY;
            case RATE_LIMITED -> PublicMarketDataQualitySummary.SourceHealth.RATE_LIMITED;
            case TIMEOUT -> PublicMarketDataQualitySummary.SourceHealth.TIMEOUT;
            case DISABLED -> PublicMarketDataQualitySummary.SourceHealth.DEGRADED;
            case TEMPORARY_FAILURE, INVALID_RESPONSE, DENIED, TRANSPORT_ERROR ->
                    PublicMarketDataQualitySummary.SourceHealth.ERROR;
            case STALE, GAP -> PublicMarketDataQualitySummary.SourceHealth.DEGRADED;
        };
    }
}
