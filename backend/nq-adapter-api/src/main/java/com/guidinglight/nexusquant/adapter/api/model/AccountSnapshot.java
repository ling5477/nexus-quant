package com.guidinglight.nexusquant.adapter.api.model;

import java.util.List;

/**
 * AccountSnapshot 聚合统一账户快照。
 *
 * @param accountId 账户 ID
 * @param venue 交易场所
 * @param balances 余额列表
 * @param positions 持仓列表
 * @param traceId 链路追踪 ID
 */
public record AccountSnapshot(
        Long accountId,
        String venue,
        List<AccountBalanceSnapshot> balances,
        List<PositionSnapshot> positions,
        String traceId
) {
}
