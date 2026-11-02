package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HistoricalKlineBar 是 adapter 返回的统一 OHLCV K 线结构。
 * <p>
 * Why:
 * 不同交易所的历史 K 线 payload 格式不同，adapter 层必须先转换为统一字段，再交给 infra bridge 映射为 core HistoricalBar。
 */
public record HistoricalKlineBar(
        String exchangeCode,
        String marketType,
        String symbol,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal volume,
        BigDecimal quoteVolume,
        Long tradeCount,
        String rawPayloadJson
) {
}
