package com.guidinglight.nexusquant.marketdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * MarketdataBarResponse 描述最小 historical bar 响应。
 */
@Schema(name = "MarketdataBarResponse", description = "Historical bar")
public record MarketdataBarResponse(
        String symbol,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal volume
) {
}

