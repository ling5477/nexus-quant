package com.guidinglight.nexusquant.marketdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * MarketdataRequestedRangeResponse 描述一次 marketdata 请求的时间窗口。
 */
@Schema(name = "MarketdataRequestedRangeResponse", description = "marketdata 请求时间窗口")
public record MarketdataRequestedRangeResponse(
        Instant startTime,
        Instant endTime
) {
}
