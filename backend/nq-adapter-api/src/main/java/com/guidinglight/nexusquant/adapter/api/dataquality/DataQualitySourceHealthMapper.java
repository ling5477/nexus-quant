package com.guidinglight.nexusquant.adapter.api.dataquality;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataEndpointCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundErrorCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundRequest;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundResult;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataQualitySummary;

import java.time.Duration;
import java.util.Objects;

/**
 * DataQualitySourceHealthMapper 把 GateO O-1 结果映射成 O-2 Data Quality Center summary。
 *
 * <p>Why: O-1 outbound result 只是一次公开行情候选结果或 disabled/fallback 结果。O-2 必须把 success、
 * high latency、429、timeout、5xx、malformed response、disabled、fallback、stale 和 gap 分别映射成
 * 稳定诊断字段，同时不暴露 trading authorization 字段，且不读取 credential、不创建 HTTP client、不触发
 * LIVE / AI / DH runtime。</p>
 */
public final class DataQualitySourceHealthMapper {

    private static final Duration HIGH_LATENCY_THRESHOLD = Duration.ofSeconds(2);

    private DataQualitySourceHealthMapper() {
    }

    /**
     * 从 O-1 outbound result 构造 O-2 summary。
     *
     * @param result O-1 脱敏结果
     * @return O-2 Data Quality summary
     */
    public static DataQualitySummary map(PublicMarketDataOutboundResult result) {
        return map(null, result);
    }

    /**
     * 从 O-1 request/result 构造 O-2 summary；request 只用于 trace/request/data window 等脱敏诊断字段。
     *
     * @param request O-1 脱敏请求；可为空
     * @param result  O-1 脱敏结果
     * @return O-2 Data Quality summary
     */
    public static DataQualitySummary map(
            PublicMarketDataOutboundRequest request,
            PublicMarketDataOutboundResult result
    ) {
        Objects.requireNonNull(result, "result must not be null");
        PublicMarketDataEndpointCategory category = request == null
                ? result.endpointCategory()
                : request.endpointCategory();
        PublicMarketDataOutboundErrorCategory errorCategory = result.errorCategory();
        DataQualitySummary.SourceStatus sourceStatus = sourceStatus(errorCategory, result);
        DataQualitySummary.SourceHealth sourceHealth = sourceHealth(errorCategory, result);
        DataQualitySummary.FreshnessStatus freshnessStatus = freshnessStatus(errorCategory, result);
        DataQualitySummary.GapStatus gapStatus = gapStatus(errorCategory, result.gapCount());
        DataQualitySummary.ErrorCategory mappedError = errorCategory(errorCategory);
        String sourceCode = result.exchange() + "_" + category.name();

        return new DataQualitySummary(
                sourceCode,
                result.exchange(),
                "UNKNOWN",
                request == null ? "UNKNOWN" : request.dataWindow(),
                dataOrigin(result.dataOrigin()),
                sourceStatus,
                sourceHealth,
                freshnessStatus,
                gapStatus,
                errorCategory == PublicMarketDataOutboundErrorCategory.NONE ? result.checkedAt() : null,
                errorCategory == PublicMarketDataOutboundErrorCategory.NONE ? null : result.checkedAt(),
                result.latency().toMillis(),
                mappedError,
                result.gapCount(),
                degradedReason(sourceHealth, freshnessStatus, gapStatus, errorCategory),
                disabledReason(errorCategory),
                request == null ? null : request.traceId(),
                request == null ? null : request.requestId());
    }

    private static DataQualitySummary.SourceStatus sourceStatus(
            PublicMarketDataOutboundErrorCategory errorCategory,
            PublicMarketDataOutboundResult result
    ) {
        return switch (errorCategory) {
            case DISABLED -> DataQualitySummary.SourceStatus.DISABLED;
            case RATE_LIMITED -> DataQualitySummary.SourceStatus.RATE_LIMITED;
            case TIMEOUT, TEMPORARY_FAILURE, INVALID_RESPONSE, DENIED, TRANSPORT_ERROR ->
                    DataQualitySummary.SourceStatus.ERROR;
            case STALE, GAP -> DataQualitySummary.SourceStatus.DEGRADED;
            case NONE -> result.latency().compareTo(HIGH_LATENCY_THRESHOLD) > 0 || result.stale() || result.gapCount() > 0
                    ? DataQualitySummary.SourceStatus.DEGRADED
                    : DataQualitySummary.SourceStatus.ENABLED;
        };
    }

    private static DataQualitySummary.SourceHealth sourceHealth(
            PublicMarketDataOutboundErrorCategory errorCategory,
            PublicMarketDataOutboundResult result
    ) {
        return switch (errorCategory) {
            case NONE -> result.latency().compareTo(HIGH_LATENCY_THRESHOLD) > 0 || result.stale() || result.gapCount() > 0
                    ? DataQualitySummary.SourceHealth.DEGRADED
                    : DataQualitySummary.SourceHealth.HEALTHY;
            case RATE_LIMITED -> DataQualitySummary.SourceHealth.RATE_LIMITED;
            case TIMEOUT -> DataQualitySummary.SourceHealth.TIMEOUT;
            case DISABLED -> DataQualitySummary.SourceHealth.UNKNOWN;
            case STALE, GAP -> DataQualitySummary.SourceHealth.DEGRADED;
            case TEMPORARY_FAILURE, INVALID_RESPONSE, DENIED, TRANSPORT_ERROR -> DataQualitySummary.SourceHealth.ERROR;
        };
    }

    private static DataQualitySummary.FreshnessStatus freshnessStatus(
            PublicMarketDataOutboundErrorCategory errorCategory,
            PublicMarketDataOutboundResult result
    ) {
        return switch (errorCategory) {
            case DISABLED -> DataQualitySummary.FreshnessStatus.DISABLED;
            case TIMEOUT, TEMPORARY_FAILURE, INVALID_RESPONSE, DENIED, RATE_LIMITED, TRANSPORT_ERROR ->
                    DataQualitySummary.FreshnessStatus.ERROR;
            case STALE -> DataQualitySummary.FreshnessStatus.STALE;
            case GAP, NONE -> result.stale()
                    ? DataQualitySummary.FreshnessStatus.STALE
                    : DataQualitySummary.FreshnessStatus.FRESH;
        };
    }

    private static DataQualitySummary.GapStatus gapStatus(
            PublicMarketDataOutboundErrorCategory errorCategory,
            int gapCount
    ) {
        if (gapCount > 0 || errorCategory == PublicMarketDataOutboundErrorCategory.GAP) {
            return DataQualitySummary.GapStatus.GAP;
        }
        if (errorCategory == PublicMarketDataOutboundErrorCategory.DISABLED) {
            return DataQualitySummary.GapStatus.UNKNOWN;
        }
        return DataQualitySummary.GapStatus.NONE;
    }

    private static DataQualitySummary.DataOrigin dataOrigin(
            PublicMarketDataQualitySummary.DataOrigin origin
    ) {
        return switch (origin) {
            case LOCAL_DB -> DataQualitySummary.DataOrigin.LOCAL_DB;
            case FIXTURE -> DataQualitySummary.DataOrigin.FIXTURE;
            case FAKE_SERVER -> DataQualitySummary.DataOrigin.FAKE_SERVER;
            case PUBLIC_OUTBOUND -> DataQualitySummary.DataOrigin.PUBLIC_CANDIDATE;
        };
    }

    private static DataQualitySummary.ErrorCategory errorCategory(
            PublicMarketDataOutboundErrorCategory category
    ) {
        return switch (category) {
            case NONE -> DataQualitySummary.ErrorCategory.NONE;
            case DISABLED -> DataQualitySummary.ErrorCategory.DISABLED;
            case DENIED -> DataQualitySummary.ErrorCategory.POLICY_DENIED;
            case RATE_LIMITED -> DataQualitySummary.ErrorCategory.RATE_LIMITED;
            case TIMEOUT -> DataQualitySummary.ErrorCategory.TIMEOUT;
            case TEMPORARY_FAILURE -> DataQualitySummary.ErrorCategory.TEMPORARY_FAILURE;
            case INVALID_RESPONSE -> DataQualitySummary.ErrorCategory.INVALID_RESPONSE;
            case STALE -> DataQualitySummary.ErrorCategory.STALE;
            case GAP -> DataQualitySummary.ErrorCategory.GAP;
            case TRANSPORT_ERROR -> DataQualitySummary.ErrorCategory.TRANSPORT_ERROR;
        };
    }

    private static String degradedReason(
            DataQualitySummary.SourceHealth sourceHealth,
            DataQualitySummary.FreshnessStatus freshnessStatus,
            DataQualitySummary.GapStatus gapStatus,
            PublicMarketDataOutboundErrorCategory errorCategory
    ) {
        if (sourceHealth == DataQualitySummary.SourceHealth.HEALTHY) {
            return null;
        }
        if (freshnessStatus == DataQualitySummary.FreshnessStatus.STALE) {
            return "public marketdata freshness is stale; diagnostic only";
        }
        if (gapStatus == DataQualitySummary.GapStatus.GAP) {
            return "public marketdata gap evidence exists; diagnostic only";
        }
        return "public marketdata source health mapped from " + errorCategory.name() + "; diagnostic only";
    }

    private static String disabledReason(PublicMarketDataOutboundErrorCategory errorCategory) {
        if (errorCategory != PublicMarketDataOutboundErrorCategory.DISABLED) {
            return null;
        }
        return "public outbound disabled by profile, feature flag or policy; system fallback remains available";
    }
}
