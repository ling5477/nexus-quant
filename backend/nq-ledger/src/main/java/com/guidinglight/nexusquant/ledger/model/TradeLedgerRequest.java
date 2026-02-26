package com.guidinglight.nexusquant.ledger.model;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeLedgerRequest 描述“成交驱动记账”的输入事实。
 *
 * Why:
 * 记账服务不应直接依赖 scheduler 内部模型，使用稳定请求模型可降低跨模块耦合并保证重放一致。
 *
 * @param tradeId 成交 ID
 * @param orderId 订单 ID
 * @param accountId 账户 ID
 * @param symbol 交易对
 * @param side 订单方向
 * @param price 成交价
 * @param qty 成交量
 * @param fee 手续费
 * @param feeCurrency 手续费币种
 * @param traceId 链路追踪 ID
 * @param ts 成交时间
 */
public record TradeLedgerRequest(
        String tradeId,
        String orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal fee,
        String feeCurrency,
        String traceId,
        Instant ts
) {
}
