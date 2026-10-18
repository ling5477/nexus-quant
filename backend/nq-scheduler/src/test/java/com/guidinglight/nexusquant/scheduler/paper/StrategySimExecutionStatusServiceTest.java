package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 验证不可见、未知及无关联执行订单不进入canonical投影；mock证明入口约束，不替代持久化终态证明。 */
class StrategySimExecutionStatusServiceTest {
    @Test
    void invisibleOrderNeverReachesCanonicalProjection() {
        var orders = mock(StrategySimOrderQuery.class);
        var executions = mock(StrategyRunExecutionRepository.class);
        var runs = mock(StrategyRunRepository.class);
        when(orders.read(7, "paper", "order")).thenReturn(Optional.empty());
        assertTrue(
                new StrategySimExecutionStatusService(orders, executions, runs)
                        .refresh(7, "paper", "order")
                        .isEmpty());
        verifyNoInteractions(executions, runs);
    }

    @Test
    void pendingUnknownAndCancelledOrdersCannotGuessExecutionFinality() {
        for (String status : List.of("ACCEPTED", "ACKED", "UNKNOWN", "CANCELLED")) {
            var orders = mock(StrategySimOrderQuery.class);
            var executions = mock(StrategyRunExecutionRepository.class);
            var runs = mock(StrategyRunRepository.class);
            when(orders.read(7, "paper", "order"))
                    .thenReturn(
                            Optional.of(
                                    new StrategySimOrderQuery.Details(
                                            "paper",
                                            Map.of("status", status, "strategy_run_id", "run"),
                                            List.of(),
                                            List.of(),
                                            List.of(),
                                            false)));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            new StrategySimExecutionStatusService(orders, executions, runs)
                                    .refresh(7, "paper", "order"));
            verifyNoInteractions(executions, runs);
        }
    }

    @Test
    void terminalOrderWithoutLinkedExecutionIsRejected() {
        var orders = mock(StrategySimOrderQuery.class);
        var executions = mock(StrategyRunExecutionRepository.class);
        var runs = mock(StrategyRunRepository.class);
        when(orders.read(7, "paper", "order"))
                .thenReturn(
                        Optional.of(
                                new StrategySimOrderQuery.Details(
                                        "paper",
                                        Map.of("status", "FILLED"),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        false)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        new StrategySimExecutionStatusService(orders, executions, runs)
                                .refresh(7, "paper", "order"));
        verifyNoInteractions(executions, runs);
    }
}
