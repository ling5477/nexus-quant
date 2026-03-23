package com.guidinglight.nexusquant.adapter.api.model;

import java.util.List;

/**
 * AccountSnapshot 聚合统一账户快照。
 *
 * @param accountId 账户 ID
 * @param exchangeCode 统一交易所标识
 * @param balances 余额列表
 * @param positions 持仓列表
 * @param traceId 链路追踪 ID
 */
public record AccountSnapshot(
        Long accountId,
        String exchangeCode,
        List<AccountBalanceSnapshot> balances,
        List<PositionSnapshot> positions,
        String traceId,
        String tradeEnv
) {

    public AccountSnapshot(Long accountId, String exchangeCode, List<AccountBalanceSnapshot> balances, List<PositionSnapshot> positions, String traceId) {
        this(accountId, exchangeCode, balances, positions, traceId, null);
    }

    public String venue() {
        return exchangeCode;
    }
}
