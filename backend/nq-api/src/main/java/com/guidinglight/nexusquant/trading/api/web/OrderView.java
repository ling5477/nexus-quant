package com.guidinglight.nexusquant.trading.api.web;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.math.BigDecimal;

/**
 * OrderView 是 GateD 的最小订单查询视图。
 * <p>
 * Why:
 * 第三批只补齐最小验收所需读模型，因此先冻结订单主键、定位字段、数量价格、状态与 trace 这些
 * 与执行闭环直接相关的字段，不在本轮扩展成完整报表视图。
 */
public record OrderView(
        String orderId,
        Long accountId,
        String venue,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        BigDecimal price,
        BigDecimal quantity,
        OrderStatus status,
        String traceId
) {

    public static OrderView from(OrderRecord order) {
        return new OrderView(
                order.orderId(),
                order.accountId(),
                order.venue(),
                order.symbol(),
                order.clientOrderId(),
                order.externalOrderId(),
                order.price(),
                order.qty(),
                order.status(),
                order.traceId()
        );
    }
}



