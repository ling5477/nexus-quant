package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import java.math.BigDecimal;

/**
 * PlaceOrderRequest 表示下单编排入口参数。
 *
 * Why:
 * Gate B 触发源可能来自 scheduler 或 HTTP，统一请求模型可减少入口差异导致的幂等口径不一致。
 *
 * @param accountId 账户 ID
 * @param strategyRunId 策略运行 ID，可空
 * @param venue 交易场所
 * @param clientOrderId 客户端幂等键
 * @param symbol 交易对
 * @param side 买卖方向
 * @param type 订单类型
 * @param price 价格，市价可空
 * @param qty 数量，必须大于 0
 * @param traceId 链路追踪 ID
 */
public record PlaceOrderRequest(
        Long accountId,
        String strategyRunId,
        String venue,
        String clientOrderId,
        String symbol,
        OrderSide side,
        OrderType type,
        BigDecimal price,
        BigDecimal qty,
        String traceId
) {
}
