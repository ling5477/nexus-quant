package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;

import java.math.BigDecimal;

/**
 * GateEStrategyManualTriggerRequest 描述 GateE-1.2 的最小手动 trigger 请求体。
 */
public record GateEStrategyManualTriggerRequest(
        String requestId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal price
) {
}
