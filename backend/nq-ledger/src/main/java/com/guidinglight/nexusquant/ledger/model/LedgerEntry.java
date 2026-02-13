package com.guidinglight.nexusquant.ledger.model;

import com.guidinglight.nexusquant.contracts.model.LedgerDirection;
import com.guidinglight.nexusquant.contracts.model.LedgerRefType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * LedgerEntry 表示不可变账本流水。
 *
 * Why:
 * docs/ARCHITECTURE.md 与 docs/DB_SCHEMA.md 强调“账本流水可重算余额”，
 * 因此骨架阶段先固定最小字段集合。
 */
public record LedgerEntry(
        String entryId,
        Long accountId,
        String currency,
        BigDecimal amount,
        LedgerDirection direction,
        LedgerRefType refType,
        String refId,
        Instant ts,
        String traceId
) {
}
