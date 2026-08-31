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
}
