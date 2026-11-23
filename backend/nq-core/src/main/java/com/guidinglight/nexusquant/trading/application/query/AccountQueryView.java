package com.guidinglight.nexusquant.trading.application.query;

import java.util.List;

/**
 * AccountQueryView 定义 trading 查询门面输出的内部账户快照聚合。
 */
public record AccountQueryView(
        Long accountId,
        String venue,
        List<AccountBalanceQueryView> balances,
        String traceId
) {
}
