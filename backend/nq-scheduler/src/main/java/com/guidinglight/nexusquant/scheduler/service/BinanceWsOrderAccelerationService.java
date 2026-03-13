package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsEventMapper;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.CancelAck;
import com.guidinglight.nexusquant.contracts.event.CancelReject;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.OrderAck;
import com.guidinglight.nexusquant.contracts.event.OrderReject;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.OrderLifecycleService;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BinanceWsOrderAccelerationService 负责 GateC-2.1 的 Binance WS 加速协同。
 * <p>
 * Why:
 * PR-BW3 只允许 WS 加速 `OrderAck/CancelAck/OrderReject/CancelReject`，
 * 且必须经过既有状态机入口，不能直接写 orders/trades/ledger。
 */
@Component
@ConditionalOnProperty(name = "nq.binance.ws.enabled", havingValue = "true")
public class BinanceWsOrderAccelerationService {

    private static final Logger log = LoggerFactory.getLogger(BinanceWsOrderAccelerationService.class);
    private static final String SOURCE = "nq-scheduler.binance-ws-accelerator";
    private static final String EXCHANGE = "BINANCE";
    private static final int LOOKUP_LIMIT = 500;

    private final OrderCommandService orderCommandService;
    private final OrderLifecycleService orderLifecycleService;
    private final AuditLogRepository auditLogRepository;
    private final EventStoreAppender eventStoreAppender;
    private final Clock clock;

    /**
     * @param orderCommandService   订单查询与 external_order_id 绑定入口
     * @param orderLifecycleService 订单生命周期入口
     * @param auditLogRepository    审计仓储
     * @param eventStoreAppender    event_store 写入器
     */
    public BinanceWsOrderAccelerationService(
            OrderCommandService orderCommandService,
            OrderLifecycleService orderLifecycleService,
            AuditLogRepository auditLogRepository,
            EventStoreAppender eventStoreAppender
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.orderLifecycleService = Objects.requireNonNull(
                orderLifecycleService,
                "orderLifecycleService must not be null"
        );
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.eventStoreAppender = Objects.requireNonNull(eventStoreAppender, "eventStoreAppender must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 消费 BW2 映射后的 Binance WS 订单事件并尝试加速状态推进。
     * <p>
     * Why:
     * 这里必须保证“单调不回退”：若 REST 已确认终态，WS 晚到消息只能留审计证据，不能再迁移状态。
     *
     * @param mappedEvent BW2 映射事件
     * @param traceId     WS 事件 trace_id
     */
    public void accelerate(BinanceWsEventMapper.MappedEvent mappedEvent, String traceId) {
        Objects.requireNonNull(mappedEvent, "mappedEvent must not be null");
        if (!TopicNames.ORDER_EVENT_V1.equals(mappedEvent.topic())) {
            return;
        }
        Object payload = mappedEvent.envelope().payload();
        if (payload instanceof OrderAck ack) {
            handleOrderAck(ack, traceId);
            return;
        }
        if (payload instanceof CancelAck ack) {
            handleCancelAck(ack, traceId);
            return;
        }
        if (payload instanceof OrderReject reject) {
            handleOrderReject(reject, traceId);
            return;
        }
        if (payload instanceof CancelReject reject) {
            handleCancelReject(reject, traceId);
        }
    }

    private void handleOrderAck(OrderAck ack, String traceId) {
        Optional<OrderRecord> maybeOrder = findOrder(ack.accountId(), ack.clientOrderId(), ack.externalOrderId());
        if (maybeOrder.isEmpty()) {
            appendAudit(traceId, "BINANCE_WS_ORDER_ACK_ORPHAN", ack.clientOrderId(), "FAIL", Map.of(
                    "venue", String.valueOf(ack.venue()),
                    "client_order_id", String.valueOf(ack.clientOrderId()),
                    "external_order_id", String.valueOf(ack.externalOrderId())
            ));
            return;
        }
        OrderRecord latest = maybeOrder.get();
        if (hasExternalIdConflict(latest, ack.externalOrderId())) {
            appendAudit(traceId, "BINANCE_WS_ORDER_ACK_EXTERNAL_ID_CONFLICT", latest.orderId(), "FAIL", Map.of(
                    "existing_external_order_id", String.valueOf(latest.externalOrderId()),
                    "ws_external_order_id", ack.externalOrderId()
            ));
            return;
        }
        latest = linkExternalIfNecessary(latest, ack.externalOrderId(), traceId);
        latest = orderCommandService.findByOrderId(latest.orderId()).orElse(latest);
        if (latest.status() == OrderStatus.ACCEPTED) {
            return;
        }
        if (isTerminal(latest.status())) {
            appendAudit(traceId, "BINANCE_WS_ORDER_ACK_IGNORED_TERMINAL", latest.orderId(), "SUCCESS", Map.of(
                    "from_status", latest.status().name(),
                    "target_status", OrderStatus.ACCEPTED.name()
            ));
            return;
        }
        try {
            orderLifecycleService.acknowledge(latest.orderId(), "BINANCE_WS_ORDER_ACK_ACCELERATE", traceId);
        } catch (IllegalStateException ex) {
            appendAudit(traceId, "BINANCE_WS_ORDER_ACK_TRANSITION_SKIPPED", latest.orderId(), "FAIL", Map.of(
                    "from_status", latest.status().name(),
                    "target_status", OrderStatus.ACCEPTED.name(),
                    "error", ex.getMessage()
            ));
        }
    }

    private void handleCancelAck(CancelAck ack, String traceId) {
        Optional<OrderRecord> maybeOrder = findOrder(ack.accountId(), ack.clientOrderId(), ack.externalOrderId());
        if (maybeOrder.isEmpty()) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_ACK_ORPHAN", ack.clientOrderId(), "FAIL", Map.of(
                    "venue", String.valueOf(ack.venue()),
                    "client_order_id", String.valueOf(ack.clientOrderId()),
                    "external_order_id", String.valueOf(ack.externalOrderId())
            ));
            return;
        }
        OrderRecord latest = maybeOrder.get();
        if (hasExternalIdConflict(latest, ack.externalOrderId())) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_ACK_EXTERNAL_ID_CONFLICT", latest.orderId(), "FAIL", Map.of(
                    "existing_external_order_id", String.valueOf(latest.externalOrderId()),
                    "ws_external_order_id", ack.externalOrderId()
            ));
            return;
        }
        latest = linkExternalIfNecessary(latest, ack.externalOrderId(), traceId);
        latest = orderCommandService.findByOrderId(latest.orderId()).orElse(latest);
        if (latest.status() == OrderStatus.CANCELLED) {
            return;
        }
        if (isTerminal(latest.status())) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_ACK_IGNORED_TERMINAL", latest.orderId(), "SUCCESS", Map.of(
                    "from_status", latest.status().name(),
                    "target_status", OrderStatus.CANCELLED.name()
            ));
            return;
        }
        try {
            if (latest.status() != OrderStatus.CANCEL_REQUESTED) {
                latest = orderLifecycleService.requestCancel(
                        latest.orderId(),
                        "BINANCE_WS_CANCEL_ACK_ACCELERATE_PREPARE",
                        traceId
                );
            }
            if (latest.status() != OrderStatus.CANCELLED) {
                orderLifecycleService.cancel(
                        latest.orderId(),
                        "BINANCE_WS_CANCEL_ACK_ACCELERATE",
                        traceId
                );
            }
        } catch (IllegalStateException ex) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_ACK_TRANSITION_SKIPPED", latest.orderId(), "FAIL", Map.of(
                    "from_status", latest.status().name(),
                    "target_status", OrderStatus.CANCELLED.name(),
                    "error", ex.getMessage()
            ));
        }
    }

    private void handleOrderReject(OrderReject reject, String traceId) {
        Optional<OrderRecord> maybeOrder = findOrder(reject.accountId(), reject.clientOrderId(), null);
        if (maybeOrder.isEmpty()) {
            appendAudit(traceId, "BINANCE_WS_ORDER_REJECT_ORPHAN", reject.clientOrderId(), "FAIL", Map.of(
                    "venue", String.valueOf(reject.venue()),
                    "client_order_id", String.valueOf(reject.clientOrderId()),
                    "reject_code", String.valueOf(reject.rejectCode())
            ));
            return;
        }
        OrderRecord latest = orderCommandService.findByOrderId(maybeOrder.get().orderId()).orElse(maybeOrder.get());
        if (latest.status() == OrderStatus.REJECTED) {
            return;
        }
        if (isTerminal(latest.status())) {
            appendAudit(traceId, "BINANCE_WS_ORDER_REJECT_IGNORED_TERMINAL", latest.orderId(), "SUCCESS", Map.of(
                    "from_status", latest.status().name(),
                    "target_status", OrderStatus.REJECTED.name(),
                    "reject_code", String.valueOf(reject.rejectCode())
            ));
            return;
        }
        try {
            orderLifecycleService.reject(latest.orderId(), reject.rejectCode(), traceId);
        } catch (IllegalStateException ex) {
            appendAudit(traceId, "BINANCE_WS_ORDER_REJECT_TRANSITION_SKIPPED", latest.orderId(), "FAIL", Map.of(
                    "from_status", latest.status().name(),
                    "target_status", OrderStatus.REJECTED.name(),
                    "reject_code", String.valueOf(reject.rejectCode()),
                    "error", ex.getMessage()
            ));
        }
    }

    private void handleCancelReject(CancelReject reject, String traceId) {
        Optional<OrderRecord> maybeOrder = findOrder(reject.accountId(), reject.clientOrderId(), reject.externalOrderId());
        if (maybeOrder.isEmpty()) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_REJECT_ORPHAN", reject.clientOrderId(), "FAIL", Map.of(
                    "venue", String.valueOf(reject.venue()),
                    "client_order_id", String.valueOf(reject.clientOrderId()),
                    "external_order_id", String.valueOf(reject.externalOrderId()),
                    "reject_code", String.valueOf(reject.rejectCode())
            ));
            return;
        }
        OrderRecord latest = maybeOrder.get();
        if (hasExternalIdConflict(latest, reject.externalOrderId())) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_REJECT_EXTERNAL_ID_CONFLICT", latest.orderId(), "FAIL", Map.of(
                    "existing_external_order_id", String.valueOf(latest.externalOrderId()),
                    "ws_external_order_id", reject.externalOrderId()
            ));
            return;
        }
        latest = linkExternalIfNecessary(latest, reject.externalOrderId(), traceId);
        latest = orderCommandService.findByOrderId(latest.orderId()).orElse(latest);
        if (latest.status() == OrderStatus.CANCEL_REJECTED) {
            return;
        }
        if (isTerminal(latest.status())) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_REJECT_IGNORED_TERMINAL", latest.orderId(), "SUCCESS", Map.of(
                    "from_status", latest.status().name(),
                    "reject_code", String.valueOf(reject.rejectCode())
            ));
            return;
        }
        if (latest.status() != OrderStatus.CANCEL_REQUESTED) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_REJECT_OUT_OF_ORDER", latest.orderId(), "SUCCESS", Map.of(
                    "from_status", latest.status().name(),
                    "reject_code", String.valueOf(reject.rejectCode()),
                    "reject_reason", String.valueOf(reject.rejectReason())
            ));
            return;
        }
        String rejectCode = reject.rejectCode() == null || reject.rejectCode().isBlank()
                ? "BINANCE_WS_CANCEL_REJECTED"
                : reject.rejectCode();
        try {
            orderLifecycleService.rejectCancel(latest.orderId(), rejectCode, traceId);
        } catch (IllegalStateException ex) {
            appendAudit(traceId, "BINANCE_WS_CANCEL_REJECT_TRANSITION_SKIPPED", latest.orderId(), "FAIL", Map.of(
                    "from_status", latest.status().name(),
                    "target_status", OrderStatus.CANCEL_REJECTED.name(),
                    "reject_code", rejectCode,
                    "error", ex.getMessage()
            ));
        }
    }

    private Optional<OrderRecord> findOrder(Long accountId, String clientOrderId, String externalOrderId) {
        if (accountId != null && clientOrderId != null && !clientOrderId.isBlank()) {
            Optional<OrderRecord> direct = orderCommandService.findByAccountAndClientOrderId(accountId, clientOrderId);
            if (direct.isPresent()) {
                return direct;
            }
        }
        return candidateOrders().stream()
                .filter(order -> matchesClientOrderId(order, clientOrderId) || matchesExternalOrderId(order, externalOrderId))
                .findFirst();
    }

    private List<OrderRecord> candidateOrders() {
        return orderCommandService.findOrdersByStatuses(
                List.of(
                        OrderStatus.SENT,
                        OrderStatus.ACCEPTED,
                        OrderStatus.PARTIALLY_FILLED,
                        OrderStatus.CANCEL_REQUESTED,
                        OrderStatus.CANCEL_REJECTED,
                        OrderStatus.FILLED,
                        OrderStatus.CANCELLED,
                        OrderStatus.REJECTED
                ),
                LOOKUP_LIMIT
        ).stream().filter(order -> EXCHANGE.equals(order.venue())).toList();
    }

    private boolean matchesClientOrderId(OrderRecord order, String clientOrderId) {
        return clientOrderId != null && !clientOrderId.isBlank() && clientOrderId.equals(order.clientOrderId());
    }

    private boolean matchesExternalOrderId(OrderRecord order, String externalOrderId) {
        return externalOrderId != null && !externalOrderId.isBlank() && externalOrderId.equals(order.externalOrderId());
    }

    private boolean hasExternalIdConflict(OrderRecord order, String wsExternalOrderId) {
        return wsExternalOrderId != null
                && !wsExternalOrderId.isBlank()
                && order.externalOrderId() != null
                && !order.externalOrderId().isBlank()
                && !wsExternalOrderId.equals(order.externalOrderId());
    }

    private OrderRecord linkExternalIfNecessary(OrderRecord order, String externalOrderId, String traceId) {
        if (externalOrderId == null || externalOrderId.isBlank() || externalOrderId.equals(order.externalOrderId())) {
            return order;
        }
        return orderCommandService.linkExternalOrderId(order.orderId(), externalOrderId, traceId);
    }

    private boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.FILLED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED;
    }

    private void appendAudit(String traceId, String action, String subjectId, String outcome, Map<String, Object> detail) {
        auditLogRepository.append("WS", action, subjectId, traceId, detail);
        AuditRecorded payload = new AuditRecorded(
                "WS",
                action,
                subjectId,
                outcome,
                detail.toString(),
                Instant.now(clock)
        );
        EventEnvelope<AuditRecorded> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                AuditRecorded.class.getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                traceId,
                subjectId == null || subjectId.isBlank() ? "BINANCE_WS_UNKNOWN" : subjectId,
                payload
        );
        eventStoreAppender.append(TopicNames.AUDIT_EVENT_V1, envelope);
        log.info("binance_ws_order_acceleration_audit action={} subject={} outcome={} trace_id={}", action, subjectId, outcome, traceId);
    }
}
