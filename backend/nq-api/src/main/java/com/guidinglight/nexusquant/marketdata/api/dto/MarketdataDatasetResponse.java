package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataDatasetResponse 描述 GateH-3 数据集响应。
 */
public record MarketdataDatasetResponse(
        UUID datasetId,
        String datasetName,
        String exchangeCode,
        String marketType,
        String symbol,
        String interval,
        Instant startTime,
        Instant endTime,
        String status,
        String qualityStatus,
        long barCount,
        long gapCount,
        String source,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        String requestJson
) {
    public static MarketdataDatasetResponse from(MarketdataDataset dataset) {
        return new MarketdataDatasetResponse(
                dataset.datasetId(),
                dataset.datasetName(),
                dataset.exchangeCode(),
                dataset.marketType(),
                dataset.symbol(),
                dataset.interval().wireValue(),
                dataset.startTime(),
                dataset.endTime(),
                dataset.status().name(),
                dataset.qualityStatus().name(),
                dataset.barCount(),
                dataset.gapCount(),
                dataset.source(),
                dataset.createdBy(),
                dataset.createdAt(),
                dataset.updatedAt(),
                dataset.requestJson()
        );
    }
}
