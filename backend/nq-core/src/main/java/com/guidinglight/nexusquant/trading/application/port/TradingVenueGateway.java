package com.guidinglight.nexusquant.trading.application.port;

import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

/**
 * TradingVenueGateway 定义 trading application 访问外部交易 venue 的统一边界。
 * <p>
 * Why:
 * `nq-core` 需要保留“下单 / 撤单 / 查单状态”的业务编排能力，
 * 但这些动作的 adapter request/ack/query 细节必须留在边界层外部。
 * 该网关把 core 允许依赖的最小语义固定下来，避免继续直接依赖 `adapter-api`。
 */
public interface TradingVenueGateway {

    /**
     * 执行一次统一下单。
     *
     * @param order   已完成本地预写并进入 `SENT` 的订单快照
     * @param request 原始业务请求
     * @return 统一边界回执
     */
    TradingPlaceGatewayResult placeOrder(OrderRecord order, PlaceOrderRequest request);

    /**
     * 执行一次统一撤单。
     *
     * @param order   已完成本地预写并进入 `CANCEL_REQUESTED` 的订单快照
     * @param request 原始业务请求
     * @return 统一边界回执
     */
    TradingCancelGatewayResult cancelOrder(OrderRecord order, CancelOrderRequest request);

    /**
     * 查询订单在外部 venue 的最小状态快照。
     *
     * @param order   本地订单快照
     * @param traceId 当前链路追踪 ID
     * @return 统一状态快照
     */
    TradingOrderStatusSnapshot getOrderStatus(OrderRecord order, String traceId);
}
