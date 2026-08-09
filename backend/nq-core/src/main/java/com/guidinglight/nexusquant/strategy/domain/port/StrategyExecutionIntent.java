package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;

import java.math.BigDecimal;

/**
 * StrategyExecutionIntent 定义 Strategy 发往 execution bridge 的稳定意图。
 *
 * <p>该类型只承载手动策略触发所需字段；Trading adapter 负责映射为既有下单应用命令，
 * Strategy 不依赖 Trading application DTO。
 */
public record StrategyExecutionIntent(
        String requestId,
        Long accountId,
        String strategyRunId,
        String venue,
        String symbol,
        String clientOrderId,
        String idempotencyKey,
        String source,
        OrderSide side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        String timeInForce,
        String traceId
) {
}
