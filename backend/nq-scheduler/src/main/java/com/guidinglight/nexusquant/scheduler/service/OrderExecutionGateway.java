package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.core.model.OrderRecord;

import java.util.List;

/**
 * OrderExecutionGateway 抽象 scheduler 对订单域的访问能力。
 * <p>
 * Why:
 * scheduler 只关心“找可撮合订单 + 触发有限生命周期动作”，通过网关隔离后可在单测中替换为内存实现。
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
     * 将订单推进到 FILLED 终态。
     *
     * @param orderId 订单 ID
     * @param reason  迁移原因
     * @param traceId 链路追踪 ID
     * @return 迁移后的订单快照
     */
    OrderRecord markFilled(String orderId, String reason, String traceId);
}
