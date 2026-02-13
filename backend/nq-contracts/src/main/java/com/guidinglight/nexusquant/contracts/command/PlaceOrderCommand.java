package com.guidinglight.nexusquant.contracts.command;

import java.math.BigDecimal;

/**
 * PlaceOrderCommand 冻结下单命令契约。
 *
 * Why:
 * docs/CONTRACTS.md 明确 client_order_id 是命令幂等键，必须在契约层可见。
 *
 * @param orderId 系统订单 ID
 * @param accountId 账户 ID
 * @param symbol 交易对
 * @param clientOrderId 客户端幂等键
 * @param side 买卖方向
 * @param type 订单类型
 * @param price 价格（市价时可空）
 * @param qty 数量
 * @param timeInForce 时效策略
 * @param strategyId 触发该命令的策略 ID（占位）
 * @param traceId 链路追踪 ID
 */
public record PlaceOrderCommand(
        String orderId,
        Long accountId,
        String symbol,
        String clientOrderId,
        String side,
        String type,
        BigDecimal price,
        BigDecimal qty,
        String timeInForce,
        String strategyId,
        String traceId
) {
}
