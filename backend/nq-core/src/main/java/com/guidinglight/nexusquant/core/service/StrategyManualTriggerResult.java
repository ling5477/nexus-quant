package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.StrategyRunStatus;

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
