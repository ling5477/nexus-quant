package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.core.service.StrategyScheduleScanResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyScheduleScanDetailResponse 描述接口响应体。
 */
@Schema(name = "StrategyScheduleScanDetailResponse", description = "接口响应体")
public record StrategyScheduleScanDetailResponse(
        @Schema(description = "scheduleJobId")
        String scheduleJobId,
        @Schema(description = "strategyId")
        String strategyId,
        @Schema(description = "outcome")
        String outcome,
        @Schema(description = "requestId")
        String requestId,
        @Schema(description = "strategyRunId")
        String strategyRunId,
        @Schema(description = "detail")
        String detail
) {
    public static StrategyScheduleScanDetailResponse from(StrategyScheduleScanResult result) {
        return new StrategyScheduleScanDetailResponse(
                result.scheduleJobId(),
                result.strategyId(),
                result.outcome().name().toLowerCase(),
                result.requestId(),
                result.strategyRunId(),
                result.detail()
        );
    }
}
