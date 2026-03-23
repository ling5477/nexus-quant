package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;

import java.math.BigDecimal;

/**
 * StrategyManualTriggerRequest 表示 GateE-1.2 的最小手动 trigger 请求。
 */
public record StrategyManualTriggerRequest(
        String strategyId,
        String requestId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal price,
        String traceId
) {
}
