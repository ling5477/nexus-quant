package com.guidinglight.nexusquant.scheduler.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.okx.service.OkxWsEventMapper;
import com.guidinglight.nexusquant.contracts.event.CancelAck;
import com.guidinglight.nexusquant.contracts.event.CancelReject;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.OrderAck;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * OkxWsOrderAccelerationServiceTest 覆盖 PR-W3 的 WS 加速幂等约束。
 */
class OkxWsOrderAccelerationServiceTest {

    /**
     * 验证重复 OrderAck 在已 ACCEPTED 状态下不会产生重复迁移。
     */
    @Test
    void shouldIgnoreDuplicateOrderAckWhenAlreadyAccepted() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        OkxWsOrderAccelerationService service = new OkxWsOrderAccelerationService(
                orderCommandService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord acceptedOrder = order("ord-ws-1", OrderStatus.ACCEPTED, "ws-ext-1");
        when(orderCommandService.findByAccountAndClientOrderId(2001L, "ws-cl-1")).thenReturn(Optional.of(acceptedOrder));
        when(orderCommandService.findByOrderId("ord-ws-1")).thenReturn(Optional.of(acceptedOrder));

        service.accelerate(orderAckEvent("ws-cl-1", "ws-ext-1"), "trc-ws-ack-1");

        verify(orderCommandService, never()).transitionOrder(any(), any(), any(), any());
        verify(orderCommandService, never()).linkExternalOrderId(any(), any(), any());
    }

    /**
     * 验证晚到 OrderAck 不会把终态订单回退到 ACCEPTED。
     */
    @Test
    void shouldNotRollbackTerminalOrderWhenLateAckArrives() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        OkxWsOrderAccelerationService service = new OkxWsOrderAccelerationService(
                orderCommandService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord filledOrder = order("ord-ws-2", OrderStatus.FILLED, "ws-ext-2");
        when(orderCommandService.findByAccountAndClientOrderId(2001L, "ws-cl-2")).thenReturn(Optional.of(filledOrder));
        when(orderCommandService.findByOrderId("ord-ws-2")).thenReturn(Optional.of(filledOrder));

        service.accelerate(orderAckEvent("ws-cl-2", "ws-ext-2"), "trc-ws-ack-2");

        verify(orderCommandService, never()).transitionOrder(any(), any(), any(), any());
        verify(auditLogRepository).append(eq("WS"), eq("WS_ORDER_ACK_IGNORED_TERMINAL"), eq("ord-ws-2"), eq("trc-ws-ack-2"), any());
        verify(eventStoreAppender).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }

    /**
     * 验证 CancelAck 会经过状态机推进为 CANCELLED。
     */
    @Test
    void shouldAccelerateCancelAckThroughStateMachine() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        OkxWsOrderAccelerationService service = new OkxWsOrderAccelerationService(
                orderCommandService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord acceptedOrder = order("ord-ws-3", OrderStatus.ACCEPTED, "ws-ext-3");
        OrderRecord cancelRequestedOrder = acceptedOrder.withStatus(OrderStatus.CANCEL_REQUESTED, "WS_CANCEL_ACK_ACCELERATE_PREPARE");
        when(orderCommandService.findByAccountAndClientOrderId(2001L, "ws-cl-3")).thenReturn(Optional.of(acceptedOrder));
        when(orderCommandService.findByOrderId("ord-ws-3")).thenReturn(Optional.of(acceptedOrder));
        when(orderCommandService.transitionOrder(
                eq("ord-ws-3"),
                eq(OrderStatus.CANCEL_REQUESTED),
                eq("WS_CANCEL_ACK_ACCELERATE_PREPARE"),
                eq("trc-ws-cancel-1")
        )).thenReturn(cancelRequestedOrder);

        service.accelerate(cancelAckEvent("ws-cl-3", "ws-ext-3"), "trc-ws-cancel-1");

        verify(orderCommandService).transitionOrder(
                "ord-ws-3",
                OrderStatus.CANCEL_REQUESTED,
                "WS_CANCEL_ACK_ACCELERATE_PREPARE",
                "trc-ws-cancel-1"
        );
        verify(orderCommandService).transitionOrder(
                "ord-ws-3",
                OrderStatus.CANCELLED,
                "WS_CANCEL_ACK_ACCELERATE",
                "trc-ws-cancel-1"
        );
    }

    /**
     * 验证 CancelReject 会把 CANCEL_REQUESTED 推进到 CANCEL_REJECTED。
     */
    @Test
    void shouldMarkCancelRejectedWhenCancelRejectArrives() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        OkxWsOrderAccelerationService service = new OkxWsOrderAccelerationService(
                orderCommandService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord cancelRequestedOrder = order("ord-ws-4", OrderStatus.CANCEL_REQUESTED, "ws-ext-4");
        when(orderCommandService.findByAccountAndClientOrderId(2001L, "ws-cl-4")).thenReturn(Optional.of(cancelRequestedOrder));
        when(orderCommandService.findByOrderId("ord-ws-4")).thenReturn(Optional.of(cancelRequestedOrder));

        service.accelerate(cancelRejectEvent("ws-cl-4", "ws-ext-4", "51604"), "trc-ws-cancel-reject-1");

        verify(orderCommandService).transitionOrder(
                "ord-ws-4",
                OrderStatus.CANCEL_REJECTED,
                "51604",
                "trc-ws-cancel-reject-1"
        );
    }

    /**
     * 验证乱序 CancelReject（当前非 CANCEL_REQUESTED）只留证据，不推进状态机。
     */
    @Test
    void shouldKeepEvidenceOnlyWhenCancelRejectOutOfOrder() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        OkxWsOrderAccelerationService service = new OkxWsOrderAccelerationService(
                orderCommandService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord acceptedOrder = order("ord-ws-5", OrderStatus.ACCEPTED, "ws-ext-5");
        when(orderCommandService.findByAccountAndClientOrderId(2001L, "ws-cl-5")).thenReturn(Optional.of(acceptedOrder));
        when(orderCommandService.findByOrderId("ord-ws-5")).thenReturn(Optional.of(acceptedOrder));

        service.accelerate(cancelRejectEvent("ws-cl-5", "ws-ext-5", "51605"), "trc-ws-cancel-reject-2");

        verify(orderCommandService, never()).transitionOrder(any(), any(), any(), any());
        verify(auditLogRepository).append(
                eq("WS"),
                eq("WS_CANCEL_REJECT_OUT_OF_ORDER"),
                eq("ord-ws-5"),
                eq("trc-ws-cancel-reject-2"),
                any()
        );
        verify(eventStoreAppender).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }

    private OkxWsEventMapper.MappedEvent orderAckEvent(String clientOrderId, String externalOrderId) {
        OrderAck payload = new OrderAck(2001L, "OKX", clientOrderId, externalOrderId, "ACCEPTED", Instant.now());
        EventEnvelope<OrderAck> envelope = new EventEnvelope<>(
                "evt-ws-ack",
                OrderAck.class.getSimpleName(),
                1,
                Instant.now(),
                "OKX_WS",
                "trc-test",
                clientOrderId,
                payload
        );
        return new OkxWsEventMapper.MappedEvent(TopicNames.ORDER_EVENT_V1, envelope);
    }

    private OkxWsEventMapper.MappedEvent cancelAckEvent(String clientOrderId, String externalOrderId) {
        CancelAck payload = new CancelAck(2001L, "OKX", clientOrderId, externalOrderId, "CANCELLED", Instant.now());
        EventEnvelope<CancelAck> envelope = new EventEnvelope<>(
                "evt-ws-cancel",
                CancelAck.class.getSimpleName(),
                1,
                Instant.now(),
                "OKX_WS",
                "trc-test",
                clientOrderId,
                payload
        );
        return new OkxWsEventMapper.MappedEvent(TopicNames.ORDER_EVENT_V1, envelope);
    }

    private OkxWsEventMapper.MappedEvent cancelRejectEvent(String clientOrderId, String externalOrderId, String rejectCode) {
        CancelReject payload = new CancelReject(
                2001L,
                "OKX",
                clientOrderId,
                externalOrderId,
                rejectCode,
                "cancel rejected",
                Instant.now()
        );
        EventEnvelope<CancelReject> envelope = new EventEnvelope<>(
                "evt-ws-cancel-reject",
                CancelReject.class.getSimpleName(),
                1,
                Instant.now(),
                "OKX_WS",
                "trc-test",
                clientOrderId,
                payload
        );
        return new OkxWsEventMapper.MappedEvent(TopicNames.ORDER_EVENT_V1, envelope);
    }

    private OrderRecord order(String orderId, OrderStatus status, String externalOrderId) {
        return new OrderRecord(
                orderId,
                2001L,
                null,
                "OKX",
                "BTC-USDT",
                "ws-cl-" + orderId.charAt(orderId.length() - 1),
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
