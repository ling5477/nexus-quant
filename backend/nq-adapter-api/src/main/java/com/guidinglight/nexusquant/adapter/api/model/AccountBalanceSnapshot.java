package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;

/**
 * AccountBalanceSnapshot 表示统一余额快照。
 *
 * @param accountId 账户 ID
 * @param exchangeCode 统一交易所标识
 * @param currency 币种
 * @param balance 总余额
 * @param available 可用余额
 * @param frozen 冻结余额
 * @param traceId 链路追踪 ID
 */
public record AccountBalanceSnapshot(
        Long accountId,
        String exchangeCode,
        String currency,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal frozen,
        String traceId,
        String tradeEnv
) {

    public AccountBalanceSnapshot(
            Long accountId,
            String exchangeCode,
            String currency,
            BigDecimal balance,
            BigDecimal available,
            BigDecimal frozen,
            String traceId
    ) {
        this(accountId, exchangeCode, currency, balance, available, frozen, traceId, null);
    }

    public String venue() {
        return exchangeCode;
    }
}
