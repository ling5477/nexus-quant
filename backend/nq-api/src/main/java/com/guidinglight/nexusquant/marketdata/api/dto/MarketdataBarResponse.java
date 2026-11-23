package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.PriceEventTimes;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * MarketdataBarResponse 描述最小 historical bar 响应。
 */
@Schema(name = "MarketdataBarResponse", description = "Historical bar")
public record MarketdataBarResponse(
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
        String qualityStatus,
        String source,
        Instant availableAt,
        String barId,
        String barIdentitySha256,
        PriceEventTimes priceEventTimes) {
    /** 旧调用方没有来源记录时明确缺失，不能把区间边界补成实际发生时点。 */
    public MarketdataBarResponse(
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
            String qualityStatus,
            String source,
            Instant availableAt) {
        this(
                exchangeCode,
                marketType,
                symbol,
                interval,
                openTime,
                closeTime,
                openPrice,
                highPrice,
                lowPrice,
                closePrice,
                volume,
                quoteVolume,
                tradeCount,
                qualityStatus,
                source,
                availableAt,
                null,
                null,
                PriceEventTimes.unavailable("NOT_PROVIDED"));
    }
}
