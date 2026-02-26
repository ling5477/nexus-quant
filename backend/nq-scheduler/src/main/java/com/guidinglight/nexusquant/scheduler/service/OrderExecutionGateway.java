package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import java.util.List;

/**
 * OrderExecutionGateway 抽象 scheduler 对订单域的访问能力。
 *
 * Why:
 * scheduler 只关心“找可撮合订单 + 推进状态”，通过网关隔离后可在单测中替换为内存实现。
 */
public interface OrderExecutionGateway {

    /**
     * 拉取待撮合订单（Gate B 仅包含 SENT/ACCEPTED）。
     *
     * @param limit 最大返回条数
     * @return 待撮合订单
     */
    List<OrderRecord> findMatchableOrders(int limit);

    /**
     * 通过状态机推进订单状态。
     *
     * @param orderId 订单 ID
     * @param nextStatus 目标状态
     * @param reason 迁移原因
     * @param traceId 链路追踪 ID
     * @return 迁移后的订单快照
     */
    OrderRecord transition(String orderId, OrderStatus nextStatus, String reason, String traceId);
}
