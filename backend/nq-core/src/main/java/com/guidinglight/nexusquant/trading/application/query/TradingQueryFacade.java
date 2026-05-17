package com.guidinglight.nexusquant.trading.application.query;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;

import java.util.List;
import java.util.Optional;

/**
 * TradingQueryFacade 定义 GateD 的最小查询闭环入口。
 * <p>
 * Why:
 * trading 查询 contract 属于应用层内部读侧端口，应该和内部 projection 一起由 core
 * 持有，避免继续留在 `nq-api` 并把 web 契约反向带进 facade。
 */
public interface TradingQueryFacade {

    /**
     * 查询正式交易工作台订单列表。
     *
     * <p>Why: GateH-1 要求 `/trading` 不再依赖手工输入单个 orderId，
     * 而是围绕强账户上下文展示订单列表。读侧仍由 infra 实现 SQL，避免 API 层直接访问数据库。</p>
     *
     * @param accountId legacy trading account id，由 API 层从正式 exchangeAccountId 解析得出
     * @param orderId 可选订单 ID 精确筛选
     * @param venue 可选交易所筛选
     * @param symbol 可选交易对筛选
     * @param status 可选订单状态筛选
     * @param tradeEnv 可选 SIM / LIVE 筛选
     * @param page 0-based 页码
     * @param size 每页大小
     * @param traceId 当前请求 trace id，仅用于读侧跟踪
     * @return 当前页订单投影
     */
    List<OrderQueryView> listOrders(
            Long accountId,
            String orderId,
            String venue,
            String symbol,
            OrderStatus status,
            String tradeEnv,
            int page,
            int size,
            String traceId
    );

    /**
     * 统计正式交易工作台订单列表总数。
     *
     * <p>Why: 前端列表需要稳定分页信息；count 与 list 使用同一组筛选条件，确保 UI 展示与查询一致。</p>
     */
    long countOrders(Long accountId, String orderId, String venue, String symbol, OrderStatus status, String tradeEnv, String traceId);

    Optional<OrderQueryView> queryOrder(String orderId, String traceId);

    Optional<TradeQueryView> queryLatestTrade(String orderId, String traceId);

    Optional<PositionQueryView> queryPosition(Long accountId, String symbol, String traceId);

    Optional<AccountQueryView> queryAccount(Long accountId, String traceId);
}
