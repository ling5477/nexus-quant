package com.guidinglight.nexusquant.ledger.contracts.model;

/**
 * LedgerPostingResult 表示记账执行结果。
 */
public record LedgerPostingResult(
        boolean posted,
        boolean idempotentHit,
        String reason
) {
}
