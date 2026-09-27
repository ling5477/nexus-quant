package com.guidinglight.nexusquant.ledger.contracts.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AccountSnapshotProjection 表示写入 `account_snapshots` 的最小账户快照。
 * sourceEventAt 保留源事件时间；当前值按持有账户币种事务锁时分配的 snapshot_id 排序。
 * POSITION_PROJECTION 的 balance 是同币种 base 仓位数量合计，available 是可用仓位合计，
 * frozen 是两者差额；LEDGER_CASH_PROJECTION 的 balance 是 NQ 当前账本现金查询结果，
 * available 等于该结果，frozen 为零。SIM 现金查询计入注资现金与成交主/费分录，
 * LIVE 现金查询沿用当前配对分录净额口径。这些值只覆盖 NQ 管理的交易投影，
 * 不等同于 OKX cashBal、availBal、frozenBal 或交易所全账户资产覆盖。
 */
public record AccountSnapshotProjection(
        Long accountId,
        String currency,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal frozen,
        Instant sourceEventAt,
        String traceId,
        String tradeEnv,
        AccountBalanceBasis balanceBasis
) {
    public AccountSnapshotProjection {
        if (accountId == null || accountId <= 0 || currency == null || currency.isBlank()
                || balance == null || available == null || frozen == null || sourceEventAt == null
                || traceId == null || traceId.isBlank() || tradeEnv == null
                || !java.util.Set.of("SIM", "LIVE").contains(tradeEnv)
                || balanceBasis == null) {
            throw new IllegalArgumentException("account snapshot provenance is required");
        }
    }
}
