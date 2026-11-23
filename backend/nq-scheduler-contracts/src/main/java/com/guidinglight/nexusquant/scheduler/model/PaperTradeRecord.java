package com.guidinglight.nexusquant.scheduler.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaperTradeRecord 表示 paper 撮合生成的成交事实。
 */
public record PaperTradeRecord(
        String tradeId,
        String orderId,
        Long accountId,
        String symbol,
        String exchange,
        String externalOrderId,
        String exchangeTradeId,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal fee,
        String feeCurrency,
        String traceId,
        Instant ts
) {
}
