package com.guidinglight.nexusquant.trading.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AccountBalanceView 表示账户某一币种的最新快照。
 * <p>
 * Why:
 * 账户只读查询按币种及环境暴露最新投影；空 provenance 明确代表旧行来源未知。
 */
public record AccountBalanceView(
        String currency,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal frozen,
        Instant snapshotTs,
        String traceId,
        String tradeEnv,
        String balanceBasis,
        String balanceScope,
        Instant recordedAt
) {
    public AccountBalanceView(String currency, BigDecimal balance, BigDecimal available,
            BigDecimal frozen, Instant snapshotTs, String traceId) {
        this(currency, balance, available, frozen, snapshotTs, traceId, null, null, null, null);
    }
}

