package com.guidinglight.nexusquant.trading.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.*;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.trading.application.port.*;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrdinaryPlaceAuthorityRepository;
import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 使用 infra 已有 Mockito 测试依赖验证 core 写端口合同，不增加 core 依赖。 */
class OrderCommandWriteServiceTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final AuditLogRepository audit = mock(AuditLogRepository.class);
    final EventPublisherPort events = mock(EventPublisherPort.class);
    final OrderCommandWriteService writes = new OrderCommandWriteService(orders,
            new InMemoryOrderStateMachine(), mock(RiskGate.class), audit, mock(RiskEventRepository.class), events,
            mock(OrdinaryPlaceAuthorityRepository.class));
    OrderRecord durable;

    @BeforeEach void repository() {
        when(orders.findByOrderId("ord-occ")).thenAnswer(c -> Optional.ofNullable(durable));
        when(orders.compareAndSetStatus(anyString(), any(), anyLong(), any(), anyString(), any()))
                .thenAnswer(c -> {
                    if (durable == null || durable.status() != c.getArgument(1)
                            || durable.version() != (long) c.getArgument(2)) return 0;
                    durable = durable.withStatus(c.getArgument(3), c.getArgument(4));
                    return 1;
                });
        when(orders.updateExternalOrderId(anyString(), anyString(), any())).thenAnswer(c -> {
            if (durable == null || durable.externalOrderId() != null) return 0;
            durable = durable.withExternalOrderId(c.getArgument(1));
            return 1;
        });
    }

    @ParameterizedTest
    @CsvSource({"NEW,RISK_PASSED", "RISK_PASSED,SENT", "SENT,ACCEPTED", "SENT,REJECTED",
            "ACCEPTED,CANCEL_REQUESTED", "CANCEL_REQUESTED,CANCELLED",
            "CANCEL_REQUESTED,CANCEL_REJECTED", "CANCEL_REJECTED,ACCEPTED"})
    void successfulTransitionAdvancesExactlyOnce(OrderStatus from, OrderStatus to) {
        durable = order(from, 7);
        OrderRecord result = writes.transitionOrder(durable.orderId(), to, "test", "trace");
        assertEquals(to, result.status());
        assertEquals(8, result.version());
        assertEquals(durable, result);
        verify(orders).compareAndSetStatus(eq("ord-occ"), eq(from), eq(7L), eq(to), eq("test"), any());
        verify(audit).append(eq("ORDER"), eq("ORDER_STATUS_TRANSITION"), anyString(), anyString(), anyMap());
    }

    @ParameterizedTest
    @CsvSource({"PLACE_ACCEPTED,SENT", "PLACE_REJECTED,SENT", "CANCEL_ACCEPTED,CANCEL_REQUESTED",
            "CANCEL_REJECTED,CANCEL_REQUESTED"})
    void staleResultsObserveFilledWithoutEvents(String response, OrderStatus expectedStatus) {
        OrderRecord expected = order(expectedStatus, 3);
        durable = order(OrderStatus.FILLED, 5);
        assertEquals(OrderStatus.FILLED, finalizeResult(response, expected));
        assertEquals(5, durable.version());
        verifyNoInteractions(events);
        verify(audit, never()).append(anyString(), eq("ORDER_STATUS_TRANSITION"), anyString(), anyString(), anyMap());
        verify(audit).append(eq("ORDER"), eq("STALE_PROVIDER_RESULT_IGNORED"), eq("ord-occ"), anyString(),
                argThat(m -> m.get("expected_version").equals(3L) && m.get("durable_version").equals(5L)
                        && m.get("provider_result").equals(response)));
        verify(orders, times(1)).compareAndSetStatus(anyString(), eq(expectedStatus), eq(3L), any(), anyString(), any());
    }

    @Test void staleCancelRejectDoesNotTakeNewGeneration() {
        durable = order(OrderStatus.CANCEL_REQUESTED, 9);
        assertEquals(OrderStatus.CANCEL_REQUESTED, finalizeResult("CANCEL_REJECTED", order(OrderStatus.CANCEL_REQUESTED, 6)));
        assertEquals(9, durable.version());
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @CsvSource({"PLACE_ACCEPTED,SENT,ACCEPTED", "PLACE_REJECTED,SENT,REJECTED",
            "CANCEL_ACCEPTED,CANCEL_REQUESTED,CANCELLED", "CANCEL_REJECTED,CANCEL_REQUESTED,CANCEL_REJECTED"})
    void currentProviderResultsAdvanceExactlyOnceAndPublish(String response, OrderStatus from, OrderStatus target) {
        durable = order(from, 4);
        assertEquals(target, finalizeResult(response, durable));
        assertEquals(5, durable.version());
        verify(events, times(1)).append(anyString(), any());
        verify(audit, never()).append(anyString(), eq("STALE_PROVIDER_RESULT_IGNORED"), anyString(), anyString(), anyMap());
    }

    @Test void duplicateTargetIsObservationWithoutSecondFact() {
        durable = order(OrderStatus.CANCELLED, 9);
        assertEquals(OrderStatus.CANCELLED, finalizeResult("CANCEL_ACCEPTED", order(OrderStatus.CANCEL_REQUESTED, 8)));
        verifyNoInteractions(events);
        verify(audit, never()).append(anyString(), eq("ORDER_CANCELLED"), anyString(), anyString(), anyMap());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 2})
    void impossibleRowCountFailsClosed(int rows) {
        durable = order(OrderStatus.SENT, 1);
        when(orders.compareAndSetStatus(anyString(), any(), anyLong(), any(), anyString(), any())).thenReturn(rows);
        assertThrows(IllegalStateException.class, () -> finalizeResult("PLACE_REJECTED", durable));
        verifyNoInteractions(events);
    }

    @Test void missingRowFailsClosed() {
        assertThrows(IllegalStateException.class, () -> finalizeResult("CANCEL_ACCEPTED", order(OrderStatus.CANCEL_REQUESTED, 2)));
        verifyNoInteractions(events);
    }

    @Test void unchangedGenerationOnFailedCasFailsClosed() {
        durable = order(OrderStatus.FILLED, 2);
        assertThrows(IllegalStateException.class, () -> finalizeResult("CANCEL_ACCEPTED", order(OrderStatus.CANCEL_REQUESTED, 2)));
        verifyNoInteractions(events);
    }

    @Test void invalidStateAndOverflowNeverReachRepository() {
        durable = order(OrderStatus.FILLED, 2);
        assertThrows(IllegalStateException.class, () -> writes.transitionOrder("ord-occ", OrderStatus.ACCEPTED, "test", "trace"));
        durable = order(OrderStatus.SENT, Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> writes.transitionOrder("ord-occ", OrderStatus.ACCEPTED, "test", "trace"));
        verify(orders, never()).compareAndSetStatus(anyString(), any(), anyLong(), any(), anyString(), any());
    }

    @Test void failedPreparationStopsPublicCommandBeforeGateway() {
        OrderRecord old = order(OrderStatus.ACCEPTED, 1);
        durable = order(OrderStatus.FILLED, 2);
        when(orders.findByOrderId("ord-occ")).thenReturn(Optional.of(old), Optional.of(durable));
        TradingVenueGateway gateway = mock(TradingVenueGateway.class);
        OrderCommandService commands = new OrderCommandService(orders, audit, events, gateway, writes);
        assertThrows(IllegalStateException.class, () -> commands.cancelOrder(cancel()));
        verifyNoInteractions(gateway);
        assertEquals(OrderStatus.FILLED, durable.status());
    }

    @Test void identityEnrichmentIsMonotonicAndPreservesVersion() {
        durable = order(OrderStatus.FILLED, 8);
        assertEquals(8, writes.linkExternalOrderId("ord-occ", "venue-A", "trace").version());
        assertEquals("venue-A", writes.linkExternalOrderId("ord-occ", "venue-A", "trace").externalOrderId());
        assertThrows(IllegalStateException.class, () -> writes.linkExternalOrderId("ord-occ", "venue-B", "trace"));
        assertEquals("venue-A", durable.externalOrderId());
        assertEquals(8, durable.version());
        verify(audit, times(1)).append(eq("ORDER"), eq("ORDER_EXTERNAL_ID_LINKED"), anyString(), anyString(), anyMap());
    }

    @Test void stalePlaceCanEnrichIdentityWithoutStatusMutation() {
        durable = order(OrderStatus.FILLED, 8);
        var ack = new TradingPlaceGatewayResult(true, "venue-A", "ACCEPTED", TradingGatewayResultCategory.ACCEPTED,
                null, Instant.EPOCH, "SIM");
        assertEquals(OrderStatus.FILLED, writes.finalizeAcceptedPlaceOrder(place(), order(OrderStatus.SENT, 2), ack, Instant.EPOCH).status());
        assertEquals("venue-A", durable.externalOrderId());
        assertEquals(8, durable.version());
        verifyNoInteractions(events);
    }

    @Test void deferredResponsesAlsoReturnDurableTruth() {
        durable = order(OrderStatus.FILLED, 8);
        assertEquals(OrderStatus.FILLED, writes.finalizeDeferredPlaceOrder(place(), order(OrderStatus.SENT, 2),
                new TradingPlaceGatewayResult(false, null, null, TradingGatewayResultCategory.DEFERRED, null, null, "SIM")).status());
        assertEquals(OrderStatus.FILLED, writes.finalizeDeferredCancelOrder(cancel(), order(OrderStatus.CANCEL_REQUESTED, 2),
                new TradingCancelGatewayResult(false, TradingGatewayResultCategory.DEFERRED, null, null, "SIM")).status());
        verifyNoInteractions(events);
    }

    private OrderStatus finalizeResult(String response, OrderRecord old) {
        return switch (response) {
            case "PLACE_ACCEPTED" -> writes.finalizeAcceptedPlaceOrder(place(), old,
                    new TradingPlaceGatewayResult(true, null, "ACCEPTED", TradingGatewayResultCategory.ACCEPTED, null, Instant.EPOCH, "SIM"), Instant.EPOCH).status();
            case "PLACE_REJECTED" -> writes.finalizeRejectedPlaceOrder(place(), old,
                    new TradingPlaceGatewayResult(false, null, null, TradingGatewayResultCategory.FATAL_FAILURE, null, Instant.EPOCH, "SIM"), Instant.EPOCH).status();
            case "CANCEL_ACCEPTED" -> writes.finalizeAcceptedCancelOrder(cancel(), old, Instant.EPOCH).status();
            default -> writes.finalizeRejectedCancelOrder(cancel(), old,
                    new TradingCancelGatewayResult(false, TradingGatewayResultCategory.FATAL_FAILURE, null, Instant.EPOCH, "SIM"), Instant.EPOCH).status();
        };
    }

    private static OrderRecord order(OrderStatus status, long version) {
        return new OrderRecord("ord-occ", 1L, null, "OKX", "BTC-USDT", "client-occ", "BUY", "LIMIT",
                BigDecimal.ONE, BigDecimal.ONE, null, status, "test", "trace", "SIM", version);
    }
    private static CancelOrderRequest cancel() { return new CancelOrderRequest("ord-occ", 1L, "client-occ", "test", "trace"); }
    private static PlaceOrderRequest place() {
        return new PlaceOrderRequest(1L, null, "OKX", "client-occ", "BTC-USDT", OrderSide.BUY,
                OrderType.LIMIT, BigDecimal.ONE, BigDecimal.ONE, "trace");
    }
}
