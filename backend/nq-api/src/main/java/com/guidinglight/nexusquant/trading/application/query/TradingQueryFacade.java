package com.guidinglight.nexusquant.trading.application.query;

import com.guidinglight.nexusquant.trading.api.web.AccountView;
import com.guidinglight.nexusquant.trading.api.web.OrderView;
import com.guidinglight.nexusquant.trading.api.web.PositionView;
import com.guidinglight.nexusquant.trading.api.web.TradeView;

import java.util.Optional;

/**
 * TradingQueryFacade 定义 GateD 的最小查询闭环入口。
 * <p>
 * Why:
 * 第四批需要把本地验收从“只能看订单”推进到“能确认订单、成交、持仓、账户快照四类最小事实”，
 * 因此 facade 在本轮冻结四类只读查询入口，避免 `nq-app` 继续直接碰底表或散落临时 SQL。
 */
public interface TradingQueryFacade {

    /**
     * 根据订单 ID 查询订单视图。
     * <p>
     * Why:
     * GateD 第三批需要给 `nq-app` 的本地验收入口补一个最小查询闭环，
     * 用于确认 place/cancel/reconcile 后订单事实已被统一读模型看到。
     */
    Optional<OrderView> queryOrder(String orderId, String traceId);

    /**
     * 根据订单 ID 查询最近一笔成交视图。
     *
     * @param orderId 系统订单 ID
     * @param traceId 链路追踪 ID；当前不参与查询条件，但保留用于日志/审计串联
     * @return 命中时返回该订单最近一笔成交
     */
    Optional<TradeView> queryLatestTrade(String orderId, String traceId);

    /**
     * 根据账户与交易对查询最小持仓视图。
     *
     * @param accountId 账户 ID
     * @param symbol    交易对，例如 `BTC-USDT`
     * @param traceId   链路追踪 ID；当前不参与查询条件，但保留用于日志/审计串联
     * @return 命中时返回持仓投影快照
     */
    Optional<PositionView> queryPosition(Long accountId, String symbol, String traceId);

    /**
     * 根据账户查询最新账户快照集合。
     *
     * @param accountId 账户 ID
     * @param traceId   链路追踪 ID；当前不参与查询条件，但保留用于日志/审计串联
     * @return 命中时返回账户的最新币种余额集合
     */
    Optional<AccountView> queryAccount(Long accountId, String traceId);
}



