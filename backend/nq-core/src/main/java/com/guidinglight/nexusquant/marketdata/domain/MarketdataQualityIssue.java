package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataQualityIssue 表示 overview 聚合出的可解释问题项。
 * <p>
 * Why:
 * Data Quality Center 需要把 gap、stale、ingestion failure 等问题聚合成可读的 topIssues；
 * issue message 只能来自本地聚合结果，不能包含 provider raw payload、credential 或交易建议。
 */
public record MarketdataQualityIssue(
        String code,
        String severity,
        long count,
        String message
) {
    public MarketdataQualityIssue {
        code = requireText(code, "code");
        severity = requireText(severity, "severity");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
