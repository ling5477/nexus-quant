package com.guidinglight.nexusquant.trading.application;

import java.time.Instant;

/**
 * RecoveryReport 描述一次恢复/回放执行结果。
 *
 * Why:
 * docs/RECOVERY_RUNBOOK.md 要求恢复输出可审计报告，骨架阶段先冻结报告字段。
 */
public record RecoveryReport(
        Instant startedAt,
        Instant finishedAt,
        long processedEventCount,
        long processedLedgerCount,
        long invalidTransitionCount,
        long imbalanceCount,
        String traceId
) {
}


