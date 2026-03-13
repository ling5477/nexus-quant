package com.guidinglight.nexusquant.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AccountBalanceView 表示账户某一币种的最新快照。
 * <p>
 * Why:
 * GateD 第四批的账户查询只需要把最新 `account_snapshots` 暴露给本地验收与 smoke，便于确认
 * ledger/快照链路是否落库，因此该视图只保留每个币种一条最新余额记录。
 */
public record AccountBalanceView(
        String currency,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal frozen,
        Instant snapshotTs,
        String traceId
) {
}
