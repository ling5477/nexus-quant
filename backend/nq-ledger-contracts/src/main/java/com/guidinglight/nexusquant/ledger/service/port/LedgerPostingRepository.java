package com.guidinglight.nexusquant.ledger.service.port;

import com.guidinglight.nexusquant.ledger.contracts.model.AccountSnapshotProjection;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingEntry;
import com.guidinglight.nexusquant.ledger.contracts.model.PositionProjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * LedgerPostingRepository 定义记账所需的持久化能力。
 */
public interface LedgerPostingRepository {

    /** 在读取投影前锁住本次发布涉及的账户币种，锁必须保持到整个记账事务结束。 */
    void lockSnapshotCurrencies(Long accountId, List<String> currencies);

    /** 原子初始化并锁定账户品种行，首次并发成交也不能绕过互斥。 */
    void lockPosition(Long accountId, String symbol, String traceId);

    /** 汇总同一账户中以该币种为 base 的仓位，用于账户币种快照。 */
    Optional<PositionProjection> findAssetPosition(Long accountId, String currency);

    boolean existsByIdempotencyKey(String idempotencyKey);

    BigDecimal currentBalance(Long accountId, String currency);

    void insertEntry(LedgerPostingEntry entry);

    void insertLedgerEvent(String entryId, String eventType, String payloadJson, String traceId);

    void insertAccountSnapshot(AccountSnapshotProjection snapshot);

    Optional<PositionProjection> findPosition(Long accountId, String symbol);

    void upsertPosition(PositionProjection projection, Instant updatedAt);
}
