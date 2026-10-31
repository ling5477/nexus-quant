package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;

/**
 * StrategyExecutionResult 定义 execution bridge 返回 Strategy 的稳定结果摘要。
 */
public record StrategyExecutionResult(
        String orderId,
        OrderStatus status,
        boolean idempotentHit
) {
}
