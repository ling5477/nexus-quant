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

    /**
     * 按幂等键查询分录是否已存在。
     *
     * @param idempotencyKey 幂等键
     * @return true 表示分录已落库
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * 查询账户某币种当前余额。
     *
     * @param accountId 账户 ID
     * @param currency  币种
     * @return 当前余额
     */
    BigDecimal currentBalance(Long accountId, String currency);

    /**
     * 插入账本分录。
     *
     * @param entry 分录快照
     */
    void insertEntry(LedgerPostingEntry entry);

    /**
     * 插入 ledger_events 记录。
     *
     * @param entryId     分录 ID
     * @param eventType   事件类型
     * @param payloadJson 事件 payload JSON
     * @param traceId     链路追踪 ID
     */
    void insertLedgerEvent(String entryId, String eventType, String payloadJson, String traceId);

    /**
     * 追加一条账户快照。
     * <p>
     * Why:
     * `account_snapshots` 是可重建投影，但 GateD 本地验收需要在成交后立刻看到最新账户余额，
     * 因此记账链路必须同步写入最小快照，避免 account query 一直空表。
     *
     * @param snapshot 账户快照
     */
    void insertAccountSnapshot(AccountSnapshotProjection snapshot);

    /**
     * 查询 positions 投影。
     *
     * @param accountId 账户 ID
     * @param symbol    交易对
     * @return 命中返回仓位
     */
    Optional<PositionProjection> findPosition(Long accountId, String symbol);

    /**
     * 更新或创建 positions 投影。
     *
     * @param projection 仓位快照
     * @param updatedAt  更新时间
     */
    void upsertPosition(PositionProjection projection, Instant updatedAt);
}
