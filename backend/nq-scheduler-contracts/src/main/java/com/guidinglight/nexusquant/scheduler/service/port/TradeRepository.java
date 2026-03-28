package com.guidinglight.nexusquant.scheduler.service.port;

import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;

import java.util.Optional;

/**
 * TradeRepository 抽象撮合模块对 trades 表的访问能力。
 */
public interface TradeRepository {

    Optional<PaperTradeRecord> findByOrderId(String orderId);

    Optional<PaperTradeRecord> findByExchangeAndExchangeTradeId(String exchange, String exchangeTradeId);

    void insert(PaperTradeRecord trade);
}
