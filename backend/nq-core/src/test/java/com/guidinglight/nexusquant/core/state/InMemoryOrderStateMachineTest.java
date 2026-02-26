package com.guidinglight.nexusquant.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import org.junit.jupiter.api.Test;

/**
 * InMemoryOrderStateMachineTest 覆盖 Gate B 状态机约束。
 *
 * Why:
 * 非法迁移是闭环里最常见的副作用放大源，必须用回归测试固定“允许/禁止”边界。
 */
class InMemoryOrderStateMachineTest {

    private final InMemoryOrderStateMachine stateMachine = new InMemoryOrderStateMachine();

    /**
     * 验证 Gate B 最小闭环合法路径。
     */
    @Test
    void shouldAllowGateBHappyPathTransitions() {
        assertEquals(OrderStatus.RISK_PASSED, stateMachine.transition(OrderStatus.NEW, OrderStatus.RISK_PASSED));
        assertEquals(OrderStatus.SENT, stateMachine.transition(OrderStatus.RISK_PASSED, OrderStatus.SENT));
        assertEquals(OrderStatus.FILLED, stateMachine.transition(OrderStatus.SENT, OrderStatus.FILLED));
    }

    /**
     * 验证至少 5 条非法迁移会被拒绝。
     */
    @Test
    void shouldRejectAtLeastFiveIllegalTransitions() {
        assertThrows(IllegalStateException.class, () -> stateMachine.transition(OrderStatus.NEW, OrderStatus.FILLED));
        assertThrows(IllegalStateException.class, () -> stateMachine.transition(OrderStatus.RISK_REJECTED, OrderStatus.SENT));
        assertThrows(IllegalStateException.class, () -> stateMachine.transition(OrderStatus.SENT, OrderStatus.RISK_PASSED));
        assertThrows(IllegalStateException.class, () -> stateMachine.transition(OrderStatus.FILLED, OrderStatus.NEW));
        assertThrows(IllegalStateException.class, () -> stateMachine.transition(OrderStatus.CANCELLED, OrderStatus.FILLED));
        assertThrows(IllegalStateException.class, () -> stateMachine.transition(OrderStatus.NEW, OrderStatus.CANCELLED));
    }
}
