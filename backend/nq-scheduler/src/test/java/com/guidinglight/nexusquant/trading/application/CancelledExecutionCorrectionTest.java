package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.trading.domain.port.OrdinaryPlaceAuthorityRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 在已有 Mockito 测试模块验证强成交证明、普通状态机边界与冲突后的重新证明。 */
class CancelledExecutionCorrectionTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final AuditLogRepository audit = mock(AuditLogRepository.class);
    private final EventPublisherPort events = mock(EventPublisherPort.class);
    private final OrderCommandWriteService service = new OrderCommandWriteService(orders,
            new InMemoryOrderStateMachine(), mock(RiskGate.class), audit, mock(RiskEventRepository.class), events,
            mock(OrdinaryPlaceAuthorityRepository.class));
    private final OrderRecord cancelled = new OrderRecord("o", 1L, null, "OKX", "BTC-USDT", "c", "BUY", "LIMIT",
            new BigDecimal("100"), new BigDecimal("10"), "ext", OrderStatus.CANCELLED, "cancel", "trace");

    @Test void partialStaysCancelledWithoutWrites() {
        proof(cancelled, "4");
        assertEquals(cancelled, service.reconcileCancelledExecution("o", "trace"));
        verify(orders, never()).compareAndSetCancelledToFilled(anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(audit, events);
    }

    @Test void exactDecimalFullProofCorrectsOnceWithAuditAndEvent() {
        proof(cancelled, "10.00000000");
        when(orders.compareAndSetCancelledToFilled(eq("o"), eq(0L), anyString(), any())).thenReturn(1);
        OrderRecord filled = service.reconcileCancelledExecution("o", "trace");
        assertEquals(OrderStatus.FILLED, filled.status()); assertEquals(1, filled.version());
        when(orders.findByOrderId("o")).thenReturn(Optional.of(filled));
        assertEquals(filled, service.reconcileCancelledExecution("o", "trace"));
        verify(orders, times(1)).compareAndSetCancelledToFilled(anyString(), anyLong(), anyString(), any());
        verify(audit).append(eq("RECONCILE"), eq("ORDER_TERMINAL_EXECUTION_CORRECTED"), eq("o"), eq("trace"), any());
        verify(events, times(1)).append(eq("order.event.v1"), any());
    }

    @Test void overfillFailsWithoutClampOrMutation() {
        proof(cancelled, "10.00000001");
        assertTrue(assertThrows(IllegalStateException.class, () -> service.reconcileCancelledExecution("o", "trace"))
                .getMessage().contains("OVERFILL"));
        verify(orders, never()).compareAndSetCancelledToFilled(anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(events);
    }

    @Test void concurrentCorrectionAlreadyFilledIsNoOp() {
        proof(cancelled, "10");
        when(orders.findByOrderId("o")).thenReturn(Optional.of(cancelled), Optional.of(cancelled.withStatus(OrderStatus.FILLED, "other")));
        assertEquals(OrderStatus.FILLED, service.reconcileCancelledExecution("o", "trace").status());
        verify(orders, times(1)).compareAndSetCancelledToFilled(anyString(), anyLong(), anyString(), any());
        verify(orders, times(2)).durableExecutedQuantity("o");
        verifyNoInteractions(events);
    }

    @Test void cancelledCasConflictRechecksLatestProofInsteadOfReusingOldQuantity() {
        proof(cancelled, "10");
        when(orders.durableExecutedQuantity("o")).thenReturn(new BigDecimal("10"), new BigDecimal("4"));
        assertEquals(OrderStatus.CANCELLED, service.reconcileCancelledExecution("o", "trace").status());
        verify(orders, times(1)).compareAndSetCancelledToFilled(anyString(), anyLong(), anyString(), any());
        verify(orders, times(2)).durableExecutedQuantity("o");
        verifyNoInteractions(events);
    }

    @Test void freshCancelledGenerationUsesNewProofAndNewVersion() {
        proof(cancelled, "10");
        when(orders.findByOrderId("o")).thenReturn(Optional.of(cancelled), Optional.of(cancelled.withStatus(OrderStatus.CANCELLED, "new")));
        when(orders.compareAndSetCancelledToFilled(eq("o"), eq(1L), anyString(), any())).thenReturn(1);
        assertEquals(2, service.reconcileCancelledExecution("o", "trace").version());
        verify(orders, times(2)).durableExecutedQuantity("o");
    }

    @Test void ordinaryLifecycleCannotRewriteCancelledEvenWithFullFills() {
        proof(cancelled, "10");
        assertThrows(IllegalStateException.class, () -> service.transitionOrder("o", OrderStatus.FILLED, "ordinary", "trace"));
        verify(orders, never()).durableExecutedQuantity(anyString());
        verify(orders, never()).compareAndSetStatus(anyString(), any(), anyLong(), any(), anyString(), any());
    }

    @Test void corruptedDurableProofIsRejected() {
        proof(cancelled, "10");
        when(orders.durableExecutedQuantity("o")).thenThrow(new IllegalStateException("INVALID_DURABLE_EXECUTION_PROOF"));
        assertThrows(IllegalStateException.class, () -> service.reconcileCancelledExecution("o", "trace"));
        verifyNoInteractions(events);
    }

    private void proof(OrderRecord order, String quantity) {
        when(orders.findByOrderId("o")).thenReturn(Optional.of(order));
        when(orders.durableExecutedQuantity("o")).thenReturn(new BigDecimal(quantity));
    }
}
