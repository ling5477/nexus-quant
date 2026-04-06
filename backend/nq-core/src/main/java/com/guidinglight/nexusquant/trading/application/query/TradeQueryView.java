package com.guidinglight.nexusquant.trading.application.query;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeQueryView 定义 trading 查询门面输出的内部成交投影。
 */
public record TradeQueryView(
        String tradeId,
        String orderId,
        Long accountId,
        String venue,
        String symbol,
        String externalOrderId,
        String exchangeTradeId,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal fee,
        String feeCurrency,
        Instant tradeTs,
        String traceId
) {
}
