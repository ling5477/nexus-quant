package com.guidinglight.nexusquant.scheduler.service.port;

import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;

import java.util.List;
import java.util.Optional;

/**
 * TradeRepository 抽象撮合模块对 trades 表的访问能力。
 */
public interface TradeRepository {

    Optional<PaperTradeRecord> findByOrderId(String orderId);

    /**
     * Lists durable Trades for one Order in deterministic oldest-first order.
     *
     * <p>The implementation must fail closed when more than {@code limit} rows exist; callers must
     * never treat a truncated result as complete recovery.</p>
     */
    List<PaperTradeRecord> findAllByOrderId(String orderId, int limit);

    Optional<PaperTradeRecord> findByExchangeAndExchangeTradeId(String exchange, String exchangeTradeId);

    void insert(PaperTradeRecord trade);

    /** 原子写入普通 OKX Trade 及其必需事件；同 fill 重放无新增，调用方按 venue fill key 读取持久身份。 */
    default void insertWithRequiredEvent(PaperTradeRecord trade) {
        throw new UnsupportedOperationException("atomic Trade/event persistence is required");
    }

    /** 从持久化 Trade 恢复必需事件；实现必须在数据库中串行化同一 Trade 的恢复。 */
    default void ensureRequiredEvent(String tradeId) {
        throw new UnsupportedOperationException("durable Trade/event recovery is required");
    }
}
