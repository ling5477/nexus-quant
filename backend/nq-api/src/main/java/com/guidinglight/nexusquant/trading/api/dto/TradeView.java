package com.guidinglight.nexusquant.trading.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeView 表示统一交易契约最小成交查询视图。
 * <p>
 * Why:
 * 成交读模型需要确认订单是否产生可追踪成交，因此固定成交主键、订单定位、
 * venue、数量价格、手续费与 trace 等调试/审计必需字段，不承担完整成交流水 API 职责。
 */
public record TradeView(
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


