package com.guidinglight.nexusquant.ledger.contracts.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AccountSnapshotProjection 表示写入 `account_snapshots` 的最小账户快照。
 * snapshotTs 保留源成交的观察时间；当前值按持有账户币种事务锁时分配的 snapshot_id 排序。
 * 币种状态与分录、仓位一同提交，不将旧成交时间当作投影发布版本。
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
