package com.guidinglight.nexusquant.adapter.api.dataquality;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataRedactor;

import java.time.Instant;
import java.util.Objects;

/**
 * DataQualitySummary 是 GateO O-2 Data Quality Center 的安全只读 summary。
 *
 * <p>Why: O-2 只把 O-1 public outbound / fake-server / fallback 结果归一成数据质量诊断，
 * 不新增 API、不连接真实交易所、不读取 credential，也不产生 trading authorization。本 record 只保存
 * source health、freshness、gap、origin、latency 与脱敏诊断字段，禁止保存 raw request、raw response、
 * raw headers、full query string、任何 credential-like material 或 trading authorization 字段。</p>
 *
 * @param sourceCode           数据源内部编码；用于排障，不表示 provider ready
 * @param exchange             exchange/source 名称
 * @param symbol               行情 symbol；未知时为 UNKNOWN
 * @param timeframe            K 线周期；未知时为 UNKNOWN
 * @param dataOrigin           数据来源；O-2 不暴露 PUBLIC_OUTBOUND
 * @param sourceStatus         source 开关与可用状态
 * @param sourceHealth         source 健康状态
 * @param freshnessStatus      数据 freshness 状态
 * @param gapStatus            缺口状态
 * @param lastSuccessAt        最近成功观测时间；无证据时为 null
 * @param lastFailureAt        最近失败观测时间；无证据时为 null
 * @param latencyMs            延迟毫秒；无证据时为 null
 * @param errorCategory        O-2 错误分类
 * @param gapCount             缺口数量；无缺口或未知时为 0
 * @param degradedReason       降级原因；会做最小脱敏
 * @param disabledReason       禁用原因；会做最小脱敏
 * @param traceId              trace id；不得携带敏感信息
 * @param requestId            request id；不得携带敏感信息
 */
public record DataQualitySummary(
        String sourceCode,
        String exchange,
        String symbol,
        String timeframe,
        DataOrigin dataOrigin,
        SourceStatus sourceStatus,
        SourceHealth sourceHealth,
        FreshnessStatus freshnessStatus,
        GapStatus gapStatus,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        Long latencyMs,
        ErrorCategory errorCategory,
        int gapCount,
        String degradedReason,
        String disabledReason,
        String traceId,
        String requestId
) {

    public DataQualitySummary {
        sourceCode = safeRequiredText(sourceCode, "sourceCode");
        exchange = safeOptionalText(exchange);
        symbol = safeOptionalText(symbol);
        timeframe = safeOptionalText(timeframe);
        dataOrigin = Objects.requireNonNull(dataOrigin, "dataOrigin must not be null");
        sourceStatus = Objects.requireNonNull(sourceStatus, "sourceStatus must not be null");
        sourceHealth = Objects.requireNonNull(sourceHealth, "sourceHealth must not be null");
        freshnessStatus = Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
        gapStatus = Objects.requireNonNull(gapStatus, "gapStatus must not be null");
        if (latencyMs != null && latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        errorCategory = Objects.requireNonNull(errorCategory, "errorCategory must not be null");
        if (gapCount < 0) {
            throw new IllegalArgumentException("gapCount must not be negative");
        }
        degradedReason = safeNullableText(degradedReason);
        disabledReason = safeNullableText(disabledReason);
        traceId = safeNullableText(traceId);
        requestId = safeNullableText(requestId);
    }

    /**
     * source 配置与当前可用状态；只描述行情源诊断，不描述交易权限。
     */
    public enum SourceStatus {
        ENABLED,
        DISABLED,
        DEGRADED,
        ERROR,
        RATE_LIMITED
    }

    /**
     * source health 诊断状态；UNKNOWN 表示证据不足，不能推断 healthy。
     */
    public enum SourceHealth {
        HEALTHY,
        DEGRADED,
        RATE_LIMITED,
        TIMEOUT,
        ERROR,
        UNKNOWN
    }

    /**
     * 数据 freshness 状态；阈值由 DataQualityFreshnessRule 统一维护，不写在 UI 或文档中。
     */
    public enum FreshnessStatus {
        FRESH,
        STALE,
        VERY_STALE,
        NO_DATA,
        ERROR,
        DISABLED
    }

    /**
     * 数据来源；O-2 不引入 PUBLIC_OUTBOUND，真实 public smoke 是否需要新 origin 留到 O-5 前审查。
     */
    public enum DataOrigin {
        LOCAL_DB,
        FIXTURE,
        FAKE_SERVER,
        PUBLIC_CANDIDATE,
        UNKNOWN
    }

    /**
     * 缺口状态；UNKNOWN 表示没有足够 expected/actual 证据，不得写成无缺口。
     */
    public enum GapStatus {
        NONE,
        GAP,
        PARTIAL,
        UNKNOWN
    }

    /**
     * O-2 稳定错误分类；用于诊断和 UI 文案，不携带 raw provider payload。
     */
    public enum ErrorCategory {
        NONE,
        DISABLED,
        POLICY_DENIED,
        RATE_LIMITED,
        TIMEOUT,
        TEMPORARY_FAILURE,
        INVALID_RESPONSE,
        STALE,
        GAP,
        TRANSPORT_ERROR,
        UNKNOWN
    }

    private static String safeRequiredText(String value, String fieldName) {
        String normalized = safeNullableText(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String safeOptionalText(String value) {
        String normalized = safeNullableText(value);
        return normalized == null || normalized.isBlank() ? "UNKNOWN" : normalized;
    }

    private static String safeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = PublicMarketDataRedactor.sanitizeMessage(value.trim());
        if (PublicMarketDataRedactor.containsCredentialLikeMarker(sanitized)) {
            return "<redacted>";
        }
        return sanitized;
    }
}
