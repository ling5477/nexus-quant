package com.guidinglight.nexusquant.marketdata.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * CreateMarketdataDatasetRequest 是 GateH-3 创建数据集的 HTTP 请求体。
 */
public record CreateMarketdataDatasetRequest(
        @NotBlank(message = "datasetName must not be blank")
        String datasetName,
        @NotBlank(message = "exchangeCode must not be blank")
        String exchangeCode,
        @NotBlank(message = "marketType must not be blank")
        String marketType,
        @NotBlank(message = "symbol must not be blank")
        String symbol,
        @NotBlank(message = "interval must not be blank")
        String interval,
        @NotNull(message = "startTime must not be null")
        Instant startTime,
        @NotNull(message = "endTime must not be null")
        Instant endTime
) {
}
