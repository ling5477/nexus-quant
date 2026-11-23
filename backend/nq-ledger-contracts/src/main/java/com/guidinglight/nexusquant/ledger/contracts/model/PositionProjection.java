package com.guidinglight.nexusquant.ledger.contracts.model;

import java.math.BigDecimal;

/**
 * PositionProjection 表示 positions 投影快照。
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
