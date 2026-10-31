package com.guidinglight.nexusquant.ledger.contracts.model;

import com.guidinglight.nexusquant.contracts.model.LedgerDirection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * LedgerPostingEntry 表示待落库的账本分录。
 */
public record LedgerPostingEntry(
        String entryId,
        Long accountId,
        String currency,
        BigDecimal delta,
        BigDecimal balanceAfter,
        LedgerDirection direction,
        String refType,
        String refId,
        String idempotencyKey,
        String traceId,
        Instant ts
) {
}
