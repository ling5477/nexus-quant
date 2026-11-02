package com.guidinglight.nexusquant.trading.domain.state;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;

/**
 * OrderStateMachine 抽象订单状态迁移规则。
 *
 * Why:
 * docs/ARCHITECTURE.md 要求严格状态机，禁止任意 setStatus。
 */
public interface OrderStateMachine {

    /**
     * 执行状态迁移。
     *
     * @param currentStatus 当前状态
     * @param nextStatus 目标状态
     * @return 迁移后的状态
     * @throws IllegalStateException 当迁移不合法时抛出
     */
    OrderStatus transition(OrderStatus currentStatus, OrderStatus nextStatus);

    /**
     * 校验迁移是否合法。
     *
     * @param currentStatus 当前状态
     * @param nextStatus 目标状态
     * @return true 表示允许迁移
     */
    boolean canTransition(OrderStatus currentStatus, OrderStatus nextStatus);
}

