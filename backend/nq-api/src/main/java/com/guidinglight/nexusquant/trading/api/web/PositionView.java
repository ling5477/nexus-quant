package com.guidinglight.nexusquant.trading.api.web;

import java.math.BigDecimal;

/**
 * PositionView 表示 GateD 最小持仓查询视图。
 * <p>
 * Why:
 * 本轮查询闭环只需要确认成交联动后 positions 投影是否已更新，因此字段冻结到账户、venue、symbol、
 * 仓位数量、可用数量、均价与 trace，不额外引入更复杂的风控/估值指标。
 */
public record PositionView(
        Long accountId,
        String venue,
        String symbol,
        BigDecimal quantity,
        BigDecimal availableQuantity,
        BigDecimal avgPrice,
        String traceId
) {
}


