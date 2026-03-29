package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;

/**
 * StrategyManualTriggerResult 表示手动 trigger 的最小返回摘要。
 */
public record StrategyManualTriggerResult(
        String strategyId,
        String strategyRunId,
        String requestId,
        String orderId,
        OrderStatus orderStatus,
        StrategyRunStatus strategyRunStatus,
        boolean idempotentHit
) {
}



