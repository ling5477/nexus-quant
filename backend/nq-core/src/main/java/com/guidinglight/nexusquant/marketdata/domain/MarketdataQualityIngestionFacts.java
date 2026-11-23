package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataQualityIngestionFacts 保存 overview 可公开展示的 ingestion 聚合事实。
 * <p>
 * Why:
 * Data Quality Center 只需要 run id、状态与成功/失败时间，不应暴露 raw_summary_json、error_message、
 * provider response、headers 或 credential-like material。
 */
public record MarketdataQualityIngestionFacts(
        Instant lastSuccessAt,
        Instant lastFailureAt,
        UUID lastIngestionRunId,
        String lastIngestionStatus
) {
    public MarketdataQualityIngestionFacts {
        lastIngestionStatus = normalize(lastIngestionStatus);
    }

    public static MarketdataQualityIngestionFacts empty() {
        return new MarketdataQualityIngestionFacts(null, null, null, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
