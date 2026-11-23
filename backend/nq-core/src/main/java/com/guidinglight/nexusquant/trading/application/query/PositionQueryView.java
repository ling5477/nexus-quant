package com.guidinglight.nexusquant.trading.application.query;

import java.math.BigDecimal;

/**
 * PositionQueryView 定义 trading 查询门面输出的内部持仓投影。
 */
public record PositionQueryView(
        Long accountId,
        String venue,
        String symbol,
        BigDecimal quantity,
        BigDecimal availableQuantity,
        BigDecimal avgPrice,
        String traceId
) {
}
