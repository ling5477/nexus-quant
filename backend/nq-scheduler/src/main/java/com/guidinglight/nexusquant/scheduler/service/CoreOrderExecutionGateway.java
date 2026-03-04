package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * CoreOrderExecutionGateway 通过 nq-core 对订单做查询与迁移。
 */
@Component
public class CoreOrderExecutionGateway implements OrderExecutionGateway {

    private final OrderCommandService orderCommandService;

    /**
     * @param orderCommandService 订单编排服务
     */
    public CoreOrderExecutionGateway(OrderCommandService orderCommandService) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
    }

    @Override
    public List<OrderRecord> findMatchableOrders(int limit) {
        // Why: GateC-0 之后只有已经收到 adapter 回执的订单才允许进入后续同步/撮合。
        return orderCommandService.findOrdersByStatuses(List.of(OrderStatus.ACCEPTED), limit);
    }

    @Override
    public OrderRecord transition(String orderId, OrderStatus nextStatus, String reason, String traceId) {
        return orderCommandService.transitionOrder(orderId, nextStatus, reason, traceId);
    }
}
