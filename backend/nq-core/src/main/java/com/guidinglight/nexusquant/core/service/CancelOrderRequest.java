package com.guidinglight.nexusquant.core.service;

/**
 * CancelOrderRequest 表示撤单编排入口参数。
 * <p>
 * Why:
 * 撤单既要支持通过 order_id 精确定位，也要支持 account_id + client_order_id 幂等定位，
 * 单独建模可以避免调用方拼装不一致导致误撤单。
 *
 * @param orderId       系统订单 ID，可空；为空时要求 accountId + clientOrderId 非空
 * @param accountId     账户 ID，可空；当 orderId 为空时必须大于 0
 * @param clientOrderId 客户端幂等键，可空；当 orderId 为空时不能为空
 * @param reason        撤单原因，不能为空
 * @param traceId       链路追踪 ID，不能为空
 */
public record CancelOrderRequest(
        String orderId,
        Long accountId,
        String clientOrderId,
        String reason,
        String traceId
) {
}
