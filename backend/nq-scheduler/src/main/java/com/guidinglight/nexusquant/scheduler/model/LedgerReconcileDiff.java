package com.guidinglight.nexusquant.scheduler.model;

import java.math.BigDecimal;

/**
 * LedgerReconcileDiff 表示账本余额与快照余额的对账差异。
 * <p>
 * Why:
 * 对账任务需要把差异结构化输出到日志与审计，便于后续恢复/补偿流程直接消费。
 *
 * @param accountId       账户 ID
 * @param currency        币种
 * @param ledgerBalance   账本聚合余额
 * @param snapshotBalance 最新快照余额
 * @param diffAmount      差异值（ledger - snapshot）
 * @param reason          差异原因，例如 SNAPSHOT_MISSING / BALANCE_MISMATCH
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
