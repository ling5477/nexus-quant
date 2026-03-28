package com.guidinglight.nexusquant.ledger.service.port;

import com.guidinglight.nexusquant.ledger.model.AccountSnapshotProjection;
import com.guidinglight.nexusquant.ledger.model.LedgerPostingEntry;
import com.guidinglight.nexusquant.ledger.model.PositionProjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * LedgerPostingRepository 定义记账所需的持久化能力。
 */
public interface LedgerPostingRepository {

    boolean existsByIdempotencyKey(String idempotencyKey);

    BigDecimal currentBalance(Long accountId, String currency);

    void insertEntry(LedgerPostingEntry entry);

    void insertLedgerEvent(String entryId, String eventType, String payloadJson, String traceId);

    void insertAccountSnapshot(AccountSnapshotProjection snapshot);

    Optional<PositionProjection> findPosition(Long accountId, String symbol);

    void upsertPosition(PositionProjection projection, Instant updatedAt);
}
