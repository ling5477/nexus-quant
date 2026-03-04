package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;

import java.math.BigDecimal;

/**
 * GateCOrderHttpRequest 描述 GateC 本地验收下单入口的最小请求体。
 * <p>
 * Why:
 * 验收触发器必须完整经过 OrderCommandService，因此请求字段直接映射服务层的 placeOrder 入参，
 * controller 不再自行补全订单意图，避免引入旁路逻辑。
 *
 * @param accountId     账户 ID，必填
 * @param strategyRunId 策略运行 ID，可空
 * @param venue         交易场所，必填
 * @param clientOrderId 客户端幂等键，必填
 * @param symbol        交易对，必填
 * @param side          买卖方向，必填
 * @param type          订单类型，必填
 * @param price         价格，LIMIT 必填；MARKET 可空
 * @param qty           数量，必填且必须大于 0
 */
public record GateCOrderHttpRequest(
        Long accountId,
        String strategyRunId,
        String venue,
        String clientOrderId,
        String symbol,
        OrderSide side,
        OrderType type,
        BigDecimal price,
        BigDecimal qty
) {
}
