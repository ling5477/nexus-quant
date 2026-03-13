package com.guidinglight.nexusquant.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeView 表示 GateD 最小成交查询视图。
 * <p>
 * Why:
 * 第四批只需要确认“订单是否产生了可追踪成交”这一最小闭环，因此先冻结成交主键、订单定位、
 * venue、数量价格、手续费与 trace 等调试/审计必需字段，不在本轮扩成完整成交流水 API。
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
