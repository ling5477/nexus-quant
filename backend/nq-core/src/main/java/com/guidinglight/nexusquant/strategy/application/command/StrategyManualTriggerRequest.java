package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchIdentity;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;

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
        String traceId,
        StrategyDispatchIdentity dispatchIdentity,
        StrategyDefinition definitionSnapshot
) {
    public StrategyManualTriggerRequest(String strategyId, String requestId, String symbol, OrderSide side,
            OrderType orderType, BigDecimal quantity, BigDecimal price, String traceId, StrategyDispatchIdentity identity) {
        this(strategyId, requestId, symbol, side, orderType, quantity, price, traceId, identity, null);
    }
    public StrategyManualTriggerRequest(String strategyId, String requestId, String symbol, OrderSide side,
                                        OrderType orderType, BigDecimal quantity, BigDecimal price, String traceId) {
        this(strategyId, requestId, symbol, side, orderType, quantity, price, traceId, null);
    }
}


