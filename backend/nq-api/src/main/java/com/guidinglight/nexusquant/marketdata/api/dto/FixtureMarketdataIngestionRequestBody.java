package com.guidinglight.nexusquant.marketdata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * FixtureMarketdataIngestionRequestBody 描述首版 fixture ingest 请求体。
 */
@Schema(name = "FixtureMarketdataIngestionRequestBody", description = "首版 marketdata fixture ingest 请求体")
public record FixtureMarketdataIngestionRequestBody(
        @NotBlank(message = "fixtureId must not be blank")
        @Schema(description = "注册 fixture ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String fixtureId,
        @NotBlank(message = "exchangeCode must not be blank")
        @Schema(description = "交易所编码", requiredMode = Schema.RequiredMode.REQUIRED)
        String exchangeCode,
        @NotBlank(message = "symbol must not be blank")
        @Schema(description = "交易对", requiredMode = Schema.RequiredMode.REQUIRED)
        String symbol,
        @NotBlank(message = "interval must not be blank")
        @Schema(description = "K 线周期", requiredMode = Schema.RequiredMode.REQUIRED)
        String interval,
        @NotNull(message = "startTime must not be null")
        @Schema(description = "请求开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startTime,
        @NotNull(message = "endTime must not be null")
        @Schema(description = "请求结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant endTime
) {
}
