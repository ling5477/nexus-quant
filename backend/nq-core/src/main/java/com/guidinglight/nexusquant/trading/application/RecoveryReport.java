package com.guidinglight.nexusquant.trading.application;

import java.time.Instant;

/**
 * RecoveryReport 描述一次恢复/回放执行结果。
 *
 * Why:
 * 该对象属于 recovery application service 的输出，不应继续放在 `domain/` 目录下制造
 * path/package 语义错位。
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
