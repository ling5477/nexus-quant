package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MarketdataQualityOverview 是 GateP Batch 2 Data Quality Center 的只读聚合 read model。
 * <p>
 * Why:
 * 该模型跨 bars、dataset coverage 和 ingestion run 汇总本地事实，只表达数据质量诊断。
 * 它不包含 tradingReady / liveReady / authorization 等字段，避免把 public marketdata readiness
 * 误解释为交易授权。
 */
public record MarketdataQualityOverview(
        MarketdataQualityOverviewScope scope,
        long totalBars,
        Long expectedBars,
        Long gapCount,
        MarketdataQualityMetric duplicateCount,
        MarketdataQualityMetric outOfOrderCount,
        MarketdataQualityMetric staleCount,
        Instant latestBarTime,
        Instant earliestBarTime,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        UUID lastIngestionRunId,
        MarketdataReadinessSourceHealth sourceHealth,
        MarketdataReadinessStatus freshnessStatus,
        MarketdataQualityStatus qualityStatus,
        MarketdataQualityDataOriginSummary dataOriginSummary,
        MarketdataQualityDatasetCoverageSummary datasetCoverageSummary,
        List<MarketdataQualityIssue> topIssues,
        Instant generatedAt
) {
    public MarketdataQualityOverview {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        if (totalBars < 0) {
            throw new IllegalArgumentException("totalBars must not be negative");
        }
        if (expectedBars != null && expectedBars < 0) {
            throw new IllegalArgumentException("expectedBars must not be negative");
        }
        if (gapCount != null && gapCount < 0) {
            throw new IllegalArgumentException("gapCount must not be negative");
        }
        duplicateCount = Objects.requireNonNull(duplicateCount, "duplicateCount must not be null");
        outOfOrderCount = Objects.requireNonNull(outOfOrderCount, "outOfOrderCount must not be null");
        staleCount = Objects.requireNonNull(staleCount, "staleCount must not be null");
        sourceHealth = Objects.requireNonNull(sourceHealth, "sourceHealth must not be null");
        freshnessStatus = Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
        qualityStatus = Objects.requireNonNull(qualityStatus, "qualityStatus must not be null");
        dataOriginSummary = Objects.requireNonNull(dataOriginSummary, "dataOriginSummary must not be null");
        datasetCoverageSummary = Objects.requireNonNull(
                datasetCoverageSummary,
                "datasetCoverageSummary must not be null"
        );
        topIssues = topIssues == null ? List.of() : List.copyOf(topIssues);
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
