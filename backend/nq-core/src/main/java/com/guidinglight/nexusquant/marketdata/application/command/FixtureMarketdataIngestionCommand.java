package com.guidinglight.nexusquant.marketdata.application.command;

import java.time.Instant;

/**
 * FixtureMarketdataIngestionCommand 描述一次显式 fixture ingest 请求。
 */
public record FixtureMarketdataIngestionCommand(
        String fixtureId,
        String exchangeCode,
        String symbol,
        String interval,
        Instant startTime,
        Instant endTime
) {
}
