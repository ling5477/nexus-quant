package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;

/**
 * HistoricalMarketDataQuery 表示一次历史行情查询请求。
 */
public record HistoricalMarketDataQuery(
        HistoricalDatasetSpec datasetSpec,
        String symbol,
        BarInterval interval,
        Instant startTime,
        Instant endTime
) {
}

