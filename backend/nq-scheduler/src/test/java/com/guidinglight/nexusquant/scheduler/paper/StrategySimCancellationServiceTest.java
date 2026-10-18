package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.application.service.OrderCommandService;
import com.guidinglight.nexusquant.trading.domain.OrderCancelFinality;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderCancelFinalityRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** 证明取消权限和不完整事实的拒绝边界；完整经济与真实取消恢复由隔离PG/HTTP证明。 */
class StrategySimCancellationServiceTest {
    @Test
    void invisibleOrderDoesNotCreateCancellation() {
        var f = new Fixture();
        when(f.visible.read(7, "paper", "order")).thenReturn(Optional.empty());
        assertTrue(f.service.cancel(7, "paper", "order", "trace").isEmpty());
        verifyNoInteractions(f.commands, f.orders, f.decisions, f.finality);
    }

    @Test
    void accessRemovedWhileWaitingForMutexNeverReachesCommand() {
        var f = new Fixture();
        var details =
                new StrategySimOrderQuery.Details(
                        "paper", Map.of("account_id", 8L), List.of(), List.of(), List.of(), false);
        when(f.visible.read(7, "paper", "order"))
                .thenReturn(Optional.of(details), Optional.empty());
        when(f.decisions.withAccountMutex(eq(8L), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        assertTrue(f.service.cancel(7, "paper", "order", "trace").isEmpty());
        verifyNoInteractions(f.commands, f.orders, f.finality);
    }

    @Test
    void realVenueAndLiveCannotUseLocalCancellationProof() {
        for (String venue : List.of("OKX", "BINANCE")) {
            var f = new Fixture();
            f.bind(venue, "SIM", OrderStatus.CANCELLED);
            assertThrows(IllegalStateException.class, () -> f.service.finish("paper", "order", 8));
            verifyNoInteractions(f.finality);
        }
        var f = new Fixture();
        f.bind("PAPER", "LIVE", OrderStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> f.service.finish("paper", "order", 8));
        verifyNoInteractions(f.finality);
    }

    @Test
    void pendingOrMissingCanonicalAckNeverTerminalizesExecution() {
        for (OrderStatus status :
                List.of(
                        OrderStatus.ACCEPTED,
                        OrderStatus.CANCEL_REQUESTED,
                        OrderStatus.CANCELLED)) {
            var f = new Fixture();
            f.bind("PAPER", "SIM", status);
            assertThrows(IllegalStateException.class, () -> f.service.finish("paper", "order", 8));
            verifyNoInteractions(f.finality);
        }
    }

    @Test
    void partialFillCannotBeReleasedAsZeroFillCancellation() {
        var f = new Fixture();
        var observed = f.bind("PAPER", "SIM", OrderStatus.CANCELLED);
        when(f.orders.hasDurableSimCancellation(observed)).thenReturn(true);
        when(f.orders.durableExecutedQuantity("order")).thenReturn(new BigDecimal("0.1"));
        assertThrows(IllegalStateException.class, () -> f.service.finish("paper", "order", 8));
        verifyNoInteractions(f.finality);
    }

    @Test
    void completeLocalProofPassesOriginalVersionToCanonicalFinality() {
        var f = new Fixture();
        var observed = f.bind("PAPER", "SIM", OrderStatus.CANCELLED);
        when(f.orders.hasDurableSimCancellation(observed)).thenReturn(true);
        when(f.orders.durableExecutedQuantity("order")).thenReturn(BigDecimal.ZERO);
        // 原执行已终态时false是幂等结果，不得据此重建订单或刷新观察版本。
        assertFalse(f.service.finish("paper", "order", 8));
        verify(f.finality).finish(new OrderCancelFinality(observed, BigDecimal.ZERO));
        verifyNoInteractions(f.commands);
    }

    private static final class Fixture {
        final StrategySimOrderQuery visible = mock(StrategySimOrderQuery.class);
        final StrategySimDecisionRepository decisions = mock(StrategySimDecisionRepository.class);
        final OrderRepository orders = mock(OrderRepository.class);
        final OrderCommandService commands = mock(OrderCommandService.class);
        final OrderCancelFinalityRepository finality = mock(OrderCancelFinalityRepository.class);
        final StrategySimCancellationService service =
                new StrategySimCancellationService(visible, decisions, orders, commands, finality);

        OrderRecord bind(String venue, String env, OrderStatus status) {
            var observed =
                    new OrderRecord(
                            "order",
                            8L,
                            "run",
                            venue,
                            "BTC-USDT",
                            "coid-sim-order",
                            "BUY",
                            "MARKET",
                            null,
                            BigDecimal.ONE,
                            "paper-external",
                            status,
                            "reason",
                            "trace",
                            env,
                            3);
            when(orders.findByOrderId("order")).thenReturn(Optional.of(observed));
            var decision = mock(StrategySimDecisionRepository.DecisionView.class);
            when(decision.paperRunId()).thenReturn("paper");
            when(decision.status()).thenReturn("ACCEPTED");
            when(decision.strategyRunId()).thenReturn("run");
            when(decisions.findByOrderId("order")).thenReturn(Optional.of(decision));
            return observed;
        }
    }
}
