package com.guidinglight.nexusquant.ledger.model;

import java.math.BigDecimal;

/**
 * PositionProjection 表示 positions 投影快照。
 *
 * @param accountId 账户 ID
 * @param symbol 交易对
 * @param qty 总仓位
 * @param availableQty 可用仓位
 * @param avgPrice 持仓均价
 * @param traceId 最近更新 trace_id
 */
public record PositionProjection(
        Long accountId,
        String symbol,
        BigDecimal qty,
        BigDecimal availableQty,
        BigDecimal avgPrice,
        String traceId
) {
}
