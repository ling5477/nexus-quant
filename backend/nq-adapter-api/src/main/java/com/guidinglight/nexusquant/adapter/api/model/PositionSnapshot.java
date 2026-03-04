package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;

/**
 * PositionSnapshot 表示统一持仓快照。
 *
 * @param accountId 账户 ID
 * @param venue 交易场所
 * @param symbol 交易对
 * @param qty 持仓数量
 * @param availableQty 可用数量
 * @param frozenQty 冻结数量
 * @param avgPrice 均价
 * @param traceId 链路追踪 ID
 */
public record PositionSnapshot(
        Long accountId,
        String venue,
        String symbol,
        BigDecimal qty,
        BigDecimal availableQty,
        BigDecimal frozenQty,
        BigDecimal avgPrice,
        String traceId
) {
}
