package com.guidinglight.nexusquant.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** 成交金额、带符号费用及原币种直接来自Trade，不按当前费率回算历史费用。 */
public record StrategyRunTradeItem(
        String tradeId,
        String orderId,
        String exchangeTradeId,
        String exchangeOrderId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal fee,
        String feeCurrency,
        Instant tradeTs) {}
