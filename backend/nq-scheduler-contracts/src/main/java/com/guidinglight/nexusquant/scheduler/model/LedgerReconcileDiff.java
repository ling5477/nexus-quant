package com.guidinglight.nexusquant.scheduler.model;

import java.math.BigDecimal;

/**
 * LedgerReconcileDiff 表示账本余额与快照余额的对账差异。
 */
public record LedgerReconcileDiff(
        Long accountId,
        String currency,
        BigDecimal ledgerBalance,
        BigDecimal snapshotBalance,
        BigDecimal diffAmount,
        String reason
) {
}
