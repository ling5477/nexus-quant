package com.guidinglight.nexusquant.paper.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "PaperTradingRunCreateRequestBody", description = "GateI-3 创建 Paper Trading run 请求体")
public record PaperTradingRunCreateRequestBody(
        @NotBlank(message = "publishId must not be blank")
        @Size(max = 64)
        String publishId,
        @NotBlank(message = "tradeEnv must not be blank")
        @Pattern(regexp = "SIM", message = "tradeEnv must be SIM because Paper Trading cannot enable LIVE")
        String tradeEnv,
        @NotBlank(message = "exchangeCode must not be blank")
        @Size(max = 32)
        String exchangeCode,
        @NotBlank(message = "marketType must not be blank")
        @Size(max = 16)
        String marketType,
        @NotBlank(message = "symbol must not be blank")
        @Size(max = 64)
        String symbol,
        @NotBlank(message = "intervalCode must not be blank")
        @Size(max = 16)
        String intervalCode,
        String configSnapshotJson
) {}
