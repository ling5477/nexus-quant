package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeItem;

import java.time.Instant;

/** 保留费用正、零、负和未知null的区别；币种与金额来自同一成交事实。 */
public record StrategyRunTradeItemResponse(
        String tradeId,
        String orderId,
        String exchangeTradeId,
        String exchangeOrderId,
        String symbol,
        String price,
        String quantity,
        String fee,
        String feeCurrency,
        Instant tradeTs) {
    public static StrategyRunTradeItemResponse from(StrategyRunTradeItem item) {
        return new StrategyRunTradeItemResponse(
                item.tradeId(),
                item.orderId(),
                item.exchangeTradeId(),
                item.exchangeOrderId(),
                item.symbol(),
                item.price().toPlainString(),
                item.quantity().toPlainString(),
                item.fee() == null ? null : item.fee().toPlainString(),
                item.feeCurrency(),
                item.tradeTs());
    }
}
