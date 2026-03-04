package com.guidinglight.nexusquant.scheduler.service.port;

import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import java.util.Optional;

/**
 * TradeRepository 抽象撮合模块对 trades 表的访问能力。
 */
public interface TradeRepository {

    /**
     * 按 order_id 查询已存在的成交。
     *
     * @param orderId 订单 ID
     * @return 命中返回成交快照
     */
    Optional<PaperTradeRecord> findByOrderId(String orderId);

    /**
     * 按交易所成交号查询已存在成交，用于交易所回放去重。
     *
     * @param exchange 成交来源，例如 OKX/PAPER
     * @param exchangeTradeId 交易所成交号
     * @return 命中返回成交快照
     */
    Optional<PaperTradeRecord> findByExchangeAndExchangeTradeId(String exchange, String exchangeTradeId);

    /**
     * 插入一笔新成交。
     *
     * @param trade 成交快照
     */
    void insert(PaperTradeRecord trade);
}
