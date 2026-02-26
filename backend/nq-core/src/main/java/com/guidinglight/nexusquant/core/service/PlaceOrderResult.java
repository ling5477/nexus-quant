package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;

/**
 * PlaceOrderResult 表示下单编排结果。
 *
 * @param orderId 系统订单 ID
 * @param status 当前订单状态
 * @param idempotentHit true 表示命中幂等返回已有订单
 */
public record PlaceOrderResult(
        String orderId,
        OrderStatus status,
        boolean idempotentHit
) {
}
