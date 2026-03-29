package com.guidinglight.nexusquant.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HistoricalBar 表示一根历史 K 线。
 */
public record HistoricalBar(
        String symbol,
        BarInterval interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal volume
) {
}

