package com.guidinglight.nexusquant.scheduler.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaperTradeRecord 表示 paper 撮合生成的成交事实。
 *
 * @param tradeId 成交 ID
 * @param orderId 关联订单 ID
 * @param accountId 账户 ID
 * @param symbol 交易对
 * @param exchange 成交来源，Gate B 固定为 PAPER
 * @param exchangeTradeId 交易所成交号，paper 可空
 * @param price 成交价格
 * @param qty 成交数量
 * @param fee 成交手续费
 * @param feeCurrency 手续费币种
 * @param traceId 链路追踪 ID
 * @param ts 成交时间
 */
public record PaperTradeRecord(
        String tradeId,
        String orderId,
        Long accountId,
        String symbol,
        String exchange,
        String exchangeTradeId,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal fee,
        String feeCurrency,
        String traceId,
        Instant ts
) {
}
