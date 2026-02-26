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
     * 插入一笔新成交。
     *
     * @param trade 成交快照
     */
    void insert(PaperTradeRecord trade);
}
