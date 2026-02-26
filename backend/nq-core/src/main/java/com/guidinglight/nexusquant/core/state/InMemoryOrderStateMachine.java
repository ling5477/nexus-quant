package com.guidinglight.nexusquant.core.state;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * InMemoryOrderStateMachine 提供 Gate A 最小状态机实现。
 * <p>
 * Why:
 * 即使本阶段不实现真实撮合，也需要一份可编译、可测试的状态迁移骨架，
 * 用于冻结合法路径并防止模块随意绕过状态机。
 */
public class InMemoryOrderStateMachine implements OrderStateMachine {

    private final Map<OrderStatus, Set<OrderStatus>> transitions;

    public InMemoryOrderStateMachine() {
        this.transitions = new EnumMap<>(OrderStatus.class);
        register(OrderStatus.NEW, OrderStatus.RISK_PASSED, OrderStatus.RISK_REJECTED, OrderStatus.VALIDATED);
        register(OrderStatus.RISK_PASSED, OrderStatus.SENT);
        register(
                OrderStatus.SENT,
                OrderStatus.ACCEPTED,
                OrderStatus.CANCEL_REQUESTED,
                OrderStatus.FILLED,
                OrderStatus.REJECTED,
                OrderStatus.FAILED
        );
        register(OrderStatus.ACCEPTED,
                OrderStatus.PARTIALLY_FILLED,
                OrderStatus.CANCEL_REQUESTED,
                OrderStatus.REJECTED,
                OrderStatus.FAILED,
                OrderStatus.FILLED);
        register(OrderStatus.VALIDATED, OrderStatus.SUBMITTING);
        register(OrderStatus.SUBMITTING, OrderStatus.ACKED, OrderStatus.REJECTED);
        register(OrderStatus.ACKED,
                OrderStatus.PARTIALLY_FILLED,
                OrderStatus.CANCEL_REQUESTED,
                OrderStatus.REJECTED,
                OrderStatus.FAILED);
        register(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.FAILED);
        register(OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED);
    }

    @Override
    public OrderStatus transition(OrderStatus currentStatus, OrderStatus nextStatus) {
        if (!canTransition(currentStatus, nextStatus)) {
            throw new IllegalStateException(
                    "invalid order transition: " + currentStatus + " -> " + nextStatus
            );
        }
        return nextStatus;
    }

    @Override
    public boolean canTransition(OrderStatus currentStatus, OrderStatus nextStatus) {
        return transitions.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class)).contains(nextStatus);
    }

    private void register(OrderStatus currentStatus, OrderStatus... nextStatuses) {
        EnumSet<OrderStatus> allowedStatuses = EnumSet.noneOf(OrderStatus.class);
        allowedStatuses.addAll(Arrays.asList(nextStatuses));
        transitions.put(currentStatus, allowedStatuses);
    }
}
