package com.guidinglight.nexusquant.scheduler.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsEventMapper;
import com.guidinglight.nexusquant.contracts.event.CancelAck;
import com.guidinglight.nexusquant.contracts.event.CancelReject;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.OrderAck;
import com.guidinglight.nexusquant.contracts.event.OrderReject;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.OrderLifecycleService;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * BinanceWsOrderAccelerationServiceTest 覆盖 PR-BW3 的 WS 加速幂等约束。
 */
class BinanceWsOrderAccelerationServiceTest {

    /**
     * 验证重复 OrderAck 在已 ACCEPTED 状态下不会产生重复迁移。
     */
    @Test
    void shouldIgnoreDuplicateOrderAckWhenAlreadyAccepted() {
        OrderCommandService orderCommandService = mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = mock(OrderLifecycleService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        BinanceWsOrderAccelerationService service = new BinanceWsOrderAccelerationService(
                orderCommandService,
                orderLifecycleService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord acceptedOrder = order("ord-bw3-1", OrderStatus.ACCEPTED, "bw3-ext-1");
        when(orderCommandService.findOrdersByStatuses(any(), eq(500))).thenReturn(List.of(acceptedOrder));
        when(orderCommandService.findByOrderId("ord-bw3-1")).thenReturn(Optional.of(acceptedOrder));

        service.accelerate(orderAckEvent("bw3-cl-1", "bw3-ext-1"), "trc-binance-ws-ack-1");

        verify(orderLifecycleService, never()).acknowledge(any(), any(), any());
        verify(orderCommandService, never()).linkExternalOrderId(any(), any(), any());
    }

    /**
     * 验证晚到 OrderAck 不会把终态订单回退到 ACCEPTED。
     */
    @Test
    void shouldNotRollbackTerminalOrderWhenLateAckArrives() {
        OrderCommandService orderCommandService = mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = mock(OrderLifecycleService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        BinanceWsOrderAccelerationService service = new BinanceWsOrderAccelerationService(
                orderCommandService,
                orderLifecycleService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord filledOrder = order("ord-bw3-2", OrderStatus.FILLED, "bw3-ext-2");
        when(orderCommandService.findOrdersByStatuses(any(), eq(500))).thenReturn(List.of(filledOrder));
        when(orderCommandService.findByOrderId("ord-bw3-2")).thenReturn(Optional.of(filledOrder));

        service.accelerate(orderAckEvent("bw3-cl-2", "bw3-ext-2"), "trc-binance-ws-ack-2");

        verify(orderLifecycleService, never()).acknowledge(any(), any(), any());
        verify(auditLogRepository).append(
                eq("WS"),
                eq("BINANCE_WS_ORDER_ACK_IGNORED_TERMINAL"),
                eq("ord-bw3-2"),
                eq("trc-binance-ws-ack-2"),
                any()
        );
        verify(eventStoreAppender).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }

    /**
     * 验证 CancelAck 会经过状态机推进为 CANCELLED。
     */
    @Test
    void shouldAccelerateCancelAckThroughStateMachine() {
        OrderCommandService orderCommandService = mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = mock(OrderLifecycleService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        BinanceWsOrderAccelerationService service = new BinanceWsOrderAccelerationService(
                orderCommandService,
                orderLifecycleService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord acceptedOrder = order("ord-bw3-3", OrderStatus.ACCEPTED, null);
        OrderRecord linkedOrder = acceptedOrder.withExternalOrderId("bw3-ext-3");
        OrderRecord cancelRequestedOrder = linkedOrder.withStatus(OrderStatus.CANCEL_REQUESTED, "BINANCE_WS_CANCEL_ACK_ACCELERATE_PREPARE");
        when(orderCommandService.findOrdersByStatuses(any(), eq(500))).thenReturn(List.of(acceptedOrder));
        when(orderCommandService.linkExternalOrderId("ord-bw3-3", "bw3-ext-3", "trc-binance-ws-cancel-1")).thenReturn(linkedOrder);
        when(orderCommandService.findByOrderId("ord-bw3-3")).thenReturn(Optional.of(linkedOrder));
        when(orderLifecycleService.requestCancel(
                eq("ord-bw3-3"),
                eq("BINANCE_WS_CANCEL_ACK_ACCELERATE_PREPARE"),
                eq("trc-binance-ws-cancel-1")
        )).thenReturn(cancelRequestedOrder);

        service.accelerate(cancelAckEvent("bw3-cl-3", "bw3-ext-3"), "trc-binance-ws-cancel-1");

        verify(orderCommandService).linkExternalOrderId("ord-bw3-3", "bw3-ext-3", "trc-binance-ws-cancel-1");
        verify(orderLifecycleService).requestCancel(
                "ord-bw3-3",
                "BINANCE_WS_CANCEL_ACK_ACCELERATE_PREPARE",
                "trc-binance-ws-cancel-1"
        );
        verify(orderLifecycleService).cancel(
                "ord-bw3-3",
                "BINANCE_WS_CANCEL_ACK_ACCELERATE",
                "trc-binance-ws-cancel-1"
        );
    }

    /**
     * 验证 CancelReject 会把 CANCEL_REQUESTED 推进到 CANCEL_REJECTED。
     */
    @Test
    void shouldMarkCancelRejectedWhenCancelRejectArrives() {
        OrderCommandService orderCommandService = mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = mock(OrderLifecycleService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        BinanceWsOrderAccelerationService service = new BinanceWsOrderAccelerationService(
                orderCommandService,
                orderLifecycleService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord cancelRequestedOrder = order("ord-bw3-4", OrderStatus.CANCEL_REQUESTED, "bw3-ext-4");
        when(orderCommandService.findOrdersByStatuses(any(), eq(500))).thenReturn(List.of(cancelRequestedOrder));
        when(orderCommandService.findByOrderId("ord-bw3-4")).thenReturn(Optional.of(cancelRequestedOrder));

        service.accelerate(cancelRejectEvent("bw3-cl-4", "bw3-ext-4", "UNKNOWN_ORDER"), "trc-binance-ws-cancel-reject-1");

        verify(orderLifecycleService).rejectCancel(
                "ord-bw3-4",
                "UNKNOWN_ORDER",
                "trc-binance-ws-cancel-reject-1"
        );
    }

    /**
     * 验证重复 OrderReject 在已 REJECTED 状态下不会产生重复副作用。
     */
    @Test
    void shouldIgnoreDuplicateOrderRejectWhenAlreadyRejected() {
        OrderCommandService orderCommandService = mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = mock(OrderLifecycleService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        BinanceWsOrderAccelerationService service = new BinanceWsOrderAccelerationService(
                orderCommandService,
                orderLifecycleService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord rejectedOrder = order("ord-bw3-5", OrderStatus.REJECTED, null);
        when(orderCommandService.findOrdersByStatuses(any(), eq(500))).thenReturn(List.of(rejectedOrder));
        when(orderCommandService.findByOrderId("ord-bw3-5")).thenReturn(Optional.of(rejectedOrder));

        service.accelerate(orderRejectEvent("bw3-cl-5", "INSUFFICIENT_BALANCES"), "trc-binance-ws-reject-1");

        verify(orderLifecycleService, never()).reject(any(), any(), any());
        verify(eventStoreAppender, never()).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }

    private BinanceWsEventMapper.MappedEvent orderAckEvent(String clientOrderId, String externalOrderId) {
        OrderAck payload = new OrderAck(null, "BINANCE", clientOrderId, externalOrderId, "ACCEPTED", Instant.now());
        EventEnvelope<OrderAck> envelope = new EventEnvelope<>(
                "evt-binance-ws-ack",
                OrderAck.class.getSimpleName(),
                1,
                Instant.now(),
                "BINANCE_WS",
                "trc-test",
                clientOrderId,
                payload
        );
        return new BinanceWsEventMapper.MappedEvent(TopicNames.ORDER_EVENT_V1, envelope);
    }

    private BinanceWsEventMapper.MappedEvent cancelAckEvent(String clientOrderId, String externalOrderId) {
        CancelAck payload = new CancelAck(null, "BINANCE", clientOrderId, externalOrderId, "CANCELLED", Instant.now());
        EventEnvelope<CancelAck> envelope = new EventEnvelope<>(
                "evt-binance-ws-cancel",
                CancelAck.class.getSimpleName(),
                1,
                Instant.now(),
                "BINANCE_WS",
                "trc-test",
                clientOrderId,
                payload
        );
        return new BinanceWsEventMapper.MappedEvent(TopicNames.ORDER_EVENT_V1, envelope);
    }

    private BinanceWsEventMapper.MappedEvent cancelRejectEvent(String clientOrderId, String externalOrderId, String rejectCode) {
        CancelReject payload = new CancelReject(
                null,
                "BINANCE",
                clientOrderId,
                externalOrderId,
                rejectCode,
                "cancel rejected",
                Instant.now()
        );
        EventEnvelope<CancelReject> envelope = new EventEnvelope<>(
                "evt-binance-ws-cancel-reject",
                CancelReject.class.getSimpleName(),
                1,
                Instant.now(),
                "BINANCE_WS",
                "trc-test",
                clientOrderId,
                payload
        );
        return new BinanceWsEventMapper.MappedEvent(TopicNames.ORDER_EVENT_V1, envelope);
    }

    private BinanceWsEventMapper.MappedEvent orderRejectEvent(String clientOrderId, String rejectCode) {
        OrderReject payload = new OrderReject(null, "BINANCE", clientOrderId, rejectCode, "reject", Instant.now());
        EventEnvelope<OrderReject> envelope = new EventEnvelope<>(
                "evt-binance-ws-reject",
                OrderReject.class.getSimpleName(),
                1,
                Instant.now(),
                "BINANCE_WS",
                "trc-test",
                clientOrderId,
                payload
        );
        return new BinanceWsEventMapper.MappedEvent(TopicNames.ORDER_EVENT_V1, envelope);
    }

    private OrderRecord order(String orderId, OrderStatus status, String externalOrderId) {
        return new OrderRecord(
                orderId,
                2001L,
                null,
                "BINANCE",
                "BTC-USDT",
                "bw3-cl-" + orderId.charAt(orderId.length() - 1),
                "BUY",
                "LIMIT",
                new BigDecimal("10000.00000000"),
                new BigDecimal("0.00100000"),
                externalOrderId,
                status,
                "TEST",
                "trc-test"
        );
    }
}


