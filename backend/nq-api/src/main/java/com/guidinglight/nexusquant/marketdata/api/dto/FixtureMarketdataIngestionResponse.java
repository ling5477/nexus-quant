package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.application.MarketdataFixtureIngestionResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * FixtureMarketdataIngestionResponse 描述 fixture ingest 的最小统计摘要。
 */
@Schema(name = "FixtureMarketdataIngestionResponse", description = "fixture ingest 统计摘要")
public record FixtureMarketdataIngestionResponse(
        String fixtureId,
        String exchangeCode,
        String symbol,
        String interval,
        MarketdataRequestedRangeResponse requestedRange,
        int rowsRead,
        int rowsInserted,
        int rowsUpdated,
        Instant startedAt,
        Instant finishedAt
) {
    public static FixtureMarketdataIngestionResponse from(MarketdataFixtureIngestionResult result) {
        return new FixtureMarketdataIngestionResponse(
                result.fixtureId(),
                result.exchangeCode(),
                result.symbol(),
                result.interval(),
                new MarketdataRequestedRangeResponse(result.requestedStartTime(), result.requestedEndTime()),
                result.rowsRead(),
                result.rowsInserted(),
                result.rowsUpdated(),
                result.startedAt(),
                result.finishedAt()
        );
    }
}
