package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyManualTriggerResponse 描述接口响应体。
 */
@Schema(name = "StrategyManualTriggerResponse", description = "接口响应体")
public record StrategyManualTriggerResponse(
        @Schema(description = "strategyId")
        String strategyId,
        @Schema(description = "strategyRunId")
        String strategyRunId,
        @Schema(description = "requestId")
        String requestId,
        @Schema(description = "orderId")
        String orderId,
        @Schema(description = "orderStatus")
        OrderStatus orderStatus,
        @Schema(description = "strategyRunStatus")
        StrategyRunStatus strategyRunStatus,
        @Schema(description = "idempotentHit")
        boolean idempotentHit
) {
    public static StrategyManualTriggerResponse from(StrategyManualTriggerResult result) {
        return new StrategyManualTriggerResponse(
                result.strategyId(),
                result.strategyRunId(),
                result.requestId(),
                result.orderId(),
                result.orderStatus(),
                result.strategyRunStatus(),
                result.idempotentHit()
        );
    }
}



