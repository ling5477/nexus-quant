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
        String marketType,
        String symbol,
        BarInterval interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal volume,
        BigDecimal quoteVolume,
        Long tradeCount,
        String qualityStatus,
        String rawPayloadJson
) {
    /**
     * 兼容 RC1 fixture 与既有单测的构造器。
     * <p>
     * Why:
     * GateH-2 把 `market_type`、成交额、成交笔数和质量状态纳入正式 bar 语义，但 RC1 已冻结的
     * fixture ingest 仍只提供基础 OHLCV。这里显式补默认值，避免调用方绕过新的 canonical 字段。
     */
    public HistoricalBar(
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
        this(
                exchangeCode,
                "SPOT",
                symbol,
                interval,
                openTime,
                closeTime,
                openPrice,
                highPrice,
                lowPrice,
                closePrice,
                volume,
                null,
                null,
                "OK",
                "{}"
        );
    }
}
