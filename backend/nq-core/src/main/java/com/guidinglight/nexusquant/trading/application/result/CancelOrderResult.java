package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;

/**
 * CancelOrderResult 表示撤单编排结果。
 *
 * @param orderId       系统订单 ID
 * @param status        当前订单状态
 * @param idempotentHit true 表示订单已处于终态或已撤销，本次未产生新副作用
 */
public record CancelOrderResult(
        String orderId,
        OrderStatus status,
        boolean idempotentHit
) {
}


