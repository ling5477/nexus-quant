package com.guidinglight.nexusquant.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HistoricalBar 表示一根历史 K 线。
 * <p>
 * Why:
 * RC1-5 要让同一套 bar 事实既能被 ingest 写入，也能被 marketdata query 与 backtest 执行复用，
 * 因此 bar 自身必须带上 `exchangeCode`，避免调用方再额外拼装身份维度。
 */
public record HistoricalBar(
        String exchangeCode,
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
