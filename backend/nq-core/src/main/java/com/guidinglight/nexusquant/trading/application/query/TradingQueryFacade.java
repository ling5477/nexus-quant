package com.guidinglight.nexusquant.trading.application.query;

import java.util.Optional;

/**
 * TradingQueryFacade 定义 GateD 的最小查询闭环入口。
 * <p>
 * Why:
 * trading 查询 contract 属于应用层内部读侧端口，应该和内部 projection 一起由 core
 * 持有，避免继续留在 `nq-api` 并把 web 契约反向带进 facade。
 */
public interface TradingQueryFacade {

    Optional<OrderQueryView> queryOrder(String orderId, String traceId);

    Optional<TradeQueryView> queryLatestTrade(String orderId, String traceId);

    Optional<PositionQueryView> queryPosition(Long accountId, String symbol, String traceId);

    Optional<AccountQueryView> queryAccount(Long accountId, String traceId);
}
