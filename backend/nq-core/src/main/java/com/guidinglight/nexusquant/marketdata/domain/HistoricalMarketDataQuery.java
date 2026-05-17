package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;

/**
 * HistoricalMarketDataQuery 表示一次历史行情查询请求。
 * <p>
 * Why:
 * marketdata query 与 backtest 执行都要按同一套 canonical 维度读取历史 K 线，
 * 因此 `exchangeCode` 也必须进入查询对象，而不是由上层隐式假设默认交易所。
 */
public record HistoricalMarketDataQuery(
        HistoricalDatasetSpec datasetSpec,
        String exchangeCode,
        String marketType,
        String symbol,
        BarInterval interval,
        Instant startTime,
        Instant endTime,
        int page,
        int size
) {
    /**
     * 兼容 RC1 调用口径，默认查询 SPOT 且限制最大 200 条。
     */
    public HistoricalMarketDataQuery(
            HistoricalDatasetSpec datasetSpec,
            String exchangeCode,
            String symbol,
            BarInterval interval,
            Instant startTime,
            Instant endTime
    ) {
        this(datasetSpec, exchangeCode, "SPOT", symbol, interval, startTime, endTime, 0, 200);
    }
}
