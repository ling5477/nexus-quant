package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataIngestionJobResponse 是行情接入任务的 API 响应。
 */
public record MarketdataIngestionJobResponse(
        UUID jobId,
        String exchangeCode,
        String marketType,
        String symbol,
        String interval,
        Instant startTime,
        Instant endTime,
        String status,
        String source,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static MarketdataIngestionJobResponse from(MarketdataIngestionJob job) {
        return new MarketdataIngestionJobResponse(
                job.jobId(),
                job.exchangeCode(),
                job.marketType(),
                job.symbol(),
                job.interval().wireValue(),
                job.startTime(),
                job.endTime(),
                job.status().name(),
                job.source(),
                job.createdBy(),
                job.createdAt(),
                job.updatedAt()
        );
    }
}
