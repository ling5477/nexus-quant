package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AdapterTradeReport 描述适配层成交回报占位。
 */
public record AdapterTradeReport(
        String tradeId,
        String orderId,
        String externalTradeId,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal feeAmount,
        String feeCurrency,
        Instant ts,
        String traceId
) {
}
