package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataQualityDatasetCoverageFacts 是 dataset coverage 表的只读聚合结果。
 * <p>
 * Why:
 * dataset coverage 已保存 expected / actual / missing / duplicate / invalid 统计，Data Quality Center
 * 应优先复用这些事实，而不是新增 migration 或重复写入另一套覆盖模型。
 */
public record MarketdataQualityDatasetCoverageFacts(
        long datasetCount,
        Long expectedBars,
        Long actualBars,
        Long missingBars,
        Long duplicateBars,
        Long invalidBars,
        UUID latestDatasetId,
        Instant latestCoverageAt
) {
    public MarketdataQualityDatasetCoverageFacts {
        if (datasetCount < 0) {
            throw new IllegalArgumentException("datasetCount must not be negative");
        }
        expectedBars = nonNegativeOrNull(expectedBars, "expectedBars");
        actualBars = nonNegativeOrNull(actualBars, "actualBars");
        missingBars = nonNegativeOrNull(missingBars, "missingBars");
        duplicateBars = nonNegativeOrNull(duplicateBars, "duplicateBars");
        invalidBars = nonNegativeOrNull(invalidBars, "invalidBars");
    }

    public static MarketdataQualityDatasetCoverageFacts empty() {
        return new MarketdataQualityDatasetCoverageFacts(0, null, null, null, null, null, null, null);
    }

    private static Long nonNegativeOrNull(Long value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
