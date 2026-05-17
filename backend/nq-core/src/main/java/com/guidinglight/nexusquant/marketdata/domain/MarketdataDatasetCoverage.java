package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataDatasetCoverage 表示一次 dataset 质量刷新结果。
 * <p>
 * Why:
 * 覆盖统计需要保留历史记录，便于解释数据集从 `INCOMPLETE` 到 `OK` 的变化过程，
 * 同时避免把每次 refresh 的排障摘要覆盖在 dataset 主表上。
 */
public record MarketdataDatasetCoverage(
        UUID coverageId,
        UUID datasetId,
        Instant rangeStartTime,
        Instant rangeEndTime,
        long expectedBars,
        long actualBars,
        long missingBars,
        long duplicateBars,
        long invalidBars,
        MarketdataQualityStatus qualityStatus,
        String summaryJson,
        Instant createdAt
) {
}
