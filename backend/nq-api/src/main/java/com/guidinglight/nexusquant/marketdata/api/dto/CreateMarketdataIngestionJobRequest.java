package com.guidinglight.nexusquant.marketdata.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * CreateMarketdataIngestionJobRequest 描述 GateH-2 创建历史 K 线接入任务的 HTTP 请求。
 */
public record CreateMarketdataIngestionJobRequest(
        @NotBlank String exchangeCode,
        @NotBlank String marketType,
        @NotBlank String symbol,
        @NotBlank String interval,
        @NotNull Instant startTime,
        @NotNull Instant endTime
) {
}
