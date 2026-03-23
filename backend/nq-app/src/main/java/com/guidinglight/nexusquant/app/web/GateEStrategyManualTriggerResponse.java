package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.StrategyRunStatus;
import com.guidinglight.nexusquant.core.service.StrategyManualTriggerResult;

/**
 * GateEStrategyManualTriggerResponse 描述 GateE-1.2 手动 trigger 的最小返回。
 */
public record GateEStrategyManualTriggerResponse(
        String strategyId,
        String strategyRunId,
        String requestId,
        String orderId,
        OrderStatus orderStatus,
        StrategyRunStatus strategyRunStatus,
        boolean idempotentHit
) {
    public static GateEStrategyManualTriggerResponse from(StrategyManualTriggerResult result) {
        return new GateEStrategyManualTriggerResponse(
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
