package com.guidinglight.nexusquant.ledger.contracts.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AccountSnapshotProjection 表示写入 `account_snapshots` 的最小账户快照。
 */
public record AccountSnapshotProjection(
        Long accountId,
        String currency,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal frozen,
        Instant snapshotTs,
        String traceId
) {
}
