package com.guidinglight.nexusquant.ledger.model;

/**
 * LedgerPostingResult 表示记账执行结果。
 *
 * @param posted true 表示记账成功
 * @param idempotentHit true 表示命中幂等并跳过重复写入
 * @param reason 结果说明
 */
public record LedgerPostingResult(
        boolean posted,
        boolean idempotentHit,
        String reason
) {
}
