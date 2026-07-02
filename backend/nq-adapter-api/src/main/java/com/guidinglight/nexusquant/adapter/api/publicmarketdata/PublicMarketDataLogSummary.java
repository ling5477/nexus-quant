package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.util.Objects;

/**
 * PublicMarketDataLogSummary 是 O-1 允许进入日志的字段白名单模型。
 *
 * <p>Why: 运行日志只允许记录 exchange、source_type、endpoint_category、status_code、error_category、
 * latency_ms、trace_id、request_id、data_window、row_count 等安全字段。该 summary 不含 raw headers、
 * raw body、full query string、credential、signature、token 或 provider raw response。</p>
 */
public record PublicMarketDataLogSummary(
        String exchange,
        PublicMarketDataQualitySummary.DataOrigin sourceType,
        PublicMarketDataEndpointCategory endpointCategory,
        int statusCode,
        PublicMarketDataOutboundErrorCategory errorCategory,
        long latencyMs,
        String traceId,
        String requestId,
        String dataWindow,
        int rowCount
) {

    public PublicMarketDataLogSummary {
        exchange = exchange == null || exchange.isBlank() ? "UNKNOWN" : exchange.trim();
        sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
        endpointCategory = Objects.requireNonNull(endpointCategory, "endpointCategory must not be null");
        errorCategory = Objects.requireNonNull(errorCategory, "errorCategory must not be null");
        traceId = traceId == null || traceId.isBlank() ? null : traceId.trim();
        requestId = requestId == null || requestId.isBlank() ? null : requestId.trim();
        dataWindow = dataWindow == null || dataWindow.isBlank() ? null : dataWindow.trim();
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        if (rowCount < 0) {
            throw new IllegalArgumentException("rowCount must not be negative");
        }
    }

    /**
     * 从请求与结果构造日志白名单 summary。
     *
     * @param request 脱敏请求模型
     * @param result  脱敏结果模型
     * @return 只含安全字段的日志 summary
     */
    public static PublicMarketDataLogSummary from(
            PublicMarketDataOutboundRequest request, PublicMarketDataOutboundResult result) {
        return new PublicMarketDataLogSummary(
                result.exchange(),
                result.dataOrigin(),
                result.endpointCategory(),
                result.statusCode(),
                result.errorCategory(),
                result.latency().toMillis(),
                request.traceId(),
                request.requestId(),
                request.dataWindow(),
                result.rowCount());
    }
}
