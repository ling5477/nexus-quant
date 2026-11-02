package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataQualityDatasetCoverageSummary 是 API 返回的 dataset coverage 聚合摘要。
 * <p>
 * Why:
 * dataset coverage 是当前 schema 中最稳定的 expected/actual/missing/duplicate/invalid 事实来源；
 * summary 只读取最新覆盖事实，不写入新 coverage，也不触发 refresh-quality。
 */
public record MarketdataQualityDatasetCoverageSummary(
        long datasetCount,
        Long expectedBars,
        Long actualBars,
        Long missingBars,
        Long duplicateBars,
        Long invalidBars,
        UUID latestDatasetId,
        Instant latestCoverageAt
) {
}
