package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import com.guidinglight.nexusquant.contracts.event.CancelAck;
import com.guidinglight.nexusquant.contracts.event.CancelReject;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.event.OrderAck;
import com.guidinglight.nexusquant.contracts.event.OrderCreated;
import com.guidinglight.nexusquant.contracts.event.OrderReject;
import com.guidinglight.nexusquant.contracts.event.OrderStatusChangedPayload;
import com.guidinglight.nexusquant.contracts.event.RiskEventRaised;
import com.guidinglight.nexusquant.contracts.event.RiskPassed;
import com.guidinglight.nexusquant.contracts.event.RiskRejected;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.application.port.TradingCancelGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayFailure;
import com.guidinglight.nexusquant.trading.application.port.TradingPlaceGatewayResult;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.trading.domain.state.OrderStateMachine;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;
import com.guidinglight.nexusquant.risk.service.RiskGate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OrderCommandWriteService 负责 `OrderCommandService` 的本地数据库写阶段。
 * <p>
 * Why:
 * 下单/撤单链路同时包含本地多表写与外部 adapter IO，二者天然不能做到同一事务原子。
 * 本类只包住“本地数据库必须一起成功或一起回滚”的阶段，
 * 让主服务可以把 adapter 调用放在事务外，同时把状态语义稳定地停在 `SENT / CANCEL_REQUESTED` 这类可恢复状态。
 */
@Service
public class OrderCommandWriteService {

    private static final String SOURCE = "nq-core.order-command-write-service";

    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final RiskGate riskGate;
    private final AuditLogRepository auditLogRepository;
    private final RiskEventRepository riskEventRepository;
    private final EventPublisherPort eventPublisherPort;
    private final Clock clock;

    public OrderCommandWriteService(
            OrderRepository orderRepository,
            OrderStateMachine orderStateMachine,
            RiskGate riskGate,
            AuditLogRepository auditLogRepository,
            RiskEventRepository riskEventRepository,
            EventPublisherPort eventPublisherPort
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.orderStateMachine = Objects.requireNonNull(orderStateMachine, "orderStateMachine must not be null");
        this.riskGate = Objects.requireNonNull(riskGate, "riskGate must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.riskEventRepository = Objects.requireNonNull(riskEventRepository, "riskEventRepository must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 预写下单链的本地事实。
     * <p>
     * Why:
     * 新建订单、风控事件、状态推进与事实链事件都属于同一个本地数据库动作，
     * 这些写入必须要么全部提交，要么全部回滚，不能把 adapter 调用包进这个事务里。
     *
     * @param request          下单请求
     * @param command          contracts 下单命令
     * @param candidateOrderId 候选订单 ID
     * @param now              请求进入时间
     * @return 本地写阶段结果；若 `completedResult` 非空，表示无需继续调用 adapter
     */
    @Transactional
    public PlaceOrderPreparation preparePlaceOrder(
            PlaceOrderRequest request,
            PlaceOrderCommand command,
            String candidateOrderId,
            Instant now
    ) {
        OrderRecord createdOrder = new OrderRecord(
                candidateOrderId,
                request.accountId(),
                request.strategyRunId(),
                request.venue(),
                request.symbol(),
                request.clientOrderId(),
                request.side().name(),
                request.type().name(),
                request.price(),
                request.quantity(),
                null,
                OrderStatus.NEW,
                "ORDER_CREATED",
                request.traceId()
        );

        try {
            orderRepository.insert(createdOrder, now);
        } catch (DuplicateKeyException ex) {
            Optional<OrderRecord> duplicated = orderRepository.findByAccountAndClientOrderId(
                    request.accountId(),
                    request.clientOrderId()
            );
            if (duplicated.isPresent()) {
                OrderRecord order = duplicated.get();
                auditLogRepository.append(
                        "ORDER",
                        "PLACE_ORDER_IDEMPOTENT_RACE",
                        order.orderId(),
                        request.traceId(),
                        detail("client_order_id", request.clientOrderId(), "reason", "duplicate_key")
                );
                return PlaceOrderPreparation.completed(new PlaceOrderResult(order.orderId(), order.status(), true));
            }
            throw ex;
        }

        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                createdOrder.clientOrderId(),
                createdOrder.traceId(),
                new OrderCreated(
                        createdOrder.orderId(),
                        createdOrder.accountId(),
                        createdOrder.strategyRunId(),
                        createdOrder.symbol(),
                        createdOrder.clientOrderId(),
                        createdOrder.side(),
                        createdOrder.type(),
                        createdOrder.price(),
                        createdOrder.qty(),
                        createdOrder.status().name(),
                        createdOrder.reason(),
                        now
                )
        );
        auditLogRepository.append(
                "ORDER",
                "ORDER_CREATED",
                createdOrder.orderId(),
                request.traceId(),
                detail(
                        "order_id", createdOrder.orderId(),
                        "client_order_id", createdOrder.clientOrderId(),
                        "request_id", request.requestId(),
                        "idempotency_key", request.idempotencyKey(),
                        "source", request.source(),
                        "venue", createdOrder.venue()
                )
        );

        RiskDecisionResult riskDecision = riskGate.evaluate(new RiskContext(command, now, request.traceId()));
        riskEventRepository.append(
                "ORDER",
                createdOrder.orderId(),
                riskDecision.decision(),
                riskDecision.ruleCode(),
                riskDecision.severity(),
                request.traceId()
        );
        publishEvent(
                TopicNames.RISK_EVENT_V1,
                createdOrder.clientOrderId(),
                createdOrder.traceId(),
                new RiskEventRaised(
                        "ORDER",
                        createdOrder.orderId(),
                        riskDecision.decision().name(),
                        riskDecision.ruleCode(),
                        riskDecision.severity().name(),
                        now
                )
        );

        if (riskDecision.decision() == RiskDecision.REJECT) {
            OrderRecord rejectedOrder = transitionOrderInternal(
                    createdOrder,
                    OrderStatus.RISK_REJECTED,
                    riskDecision.ruleCode(),
                    request.traceId()
            );
            publishEvent(
                    TopicNames.ORDER_EVENT_V1,
                    rejectedOrder.clientOrderId(),
                    rejectedOrder.traceId(),
                    new RiskRejected(
                            rejectedOrder.orderId(),
                            rejectedOrder.clientOrderId(),
                            riskDecision.decision().name(),
                            riskDecision.ruleCode(),
                            riskDecision.severity().name(),
                            now
                    )
            );
            auditLogRepository.append(
                    "ORDER",
                    "RISK_REJECTED",
                    rejectedOrder.orderId(),
                    request.traceId(),
                    detail(
                            "rule_code", riskDecision.ruleCode(),
                            "rule_name", riskDecision.ruleName(),
                            "reject_reason", riskDecision.rejectReason(),
                            "hard_reject", riskDecision.hardReject(),
                            "request_id", request.requestId(),
                            "venue", rejectedOrder.venue()
                    )
            );
            return PlaceOrderPreparation.completed(
                    new PlaceOrderResult(rejectedOrder.orderId(), rejectedOrder.status(), false)
            );
        }

        OrderRecord riskPassedOrder = transitionOrderInternal(
                createdOrder,
                OrderStatus.RISK_PASSED,
                riskDecision.ruleCode(),
                request.traceId()
        );
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                riskPassedOrder.clientOrderId(),
                riskPassedOrder.traceId(),
                new RiskPassed(
                        riskPassedOrder.orderId(),
                        riskPassedOrder.clientOrderId(),
                        riskDecision.decision().name(),
                        riskDecision.ruleCode(),
                        now
                )
        );

        OrderRecord sentOrder = transitionOrderInternal(
                riskPassedOrder,
                OrderStatus.SENT,
                "ORDER_ROUTED_TO_ADAPTER",
                request.traceId()
        );
        return PlaceOrderPreparation.readyForAdapter(sentOrder);
    }

    /**
     * 在 adapter 接受后补写本地确认事实。
     * Why:
     * 该阶段已经发生外部副作用，因此本地写失败时不能伪装成成功；
     * 但本地多表写仍必须在一个事务内一起完成，避免留下只写了 external_order_id 却没有状态事件的半套数据。
     */
    @Transactional
    public PlaceOrderResult finalizeAcceptedPlaceOrder(
            PlaceOrderRequest request,
            OrderRecord sentOrder,
            TradingPlaceGatewayResult gatewayResult,
            Instant ackTime
    ) {
        String exchangeOrderId = gatewayResult.exchangeOrderId();
        OrderRecord acceptedSnapshot = sentOrder;
        if (exchangeOrderId != null && !exchangeOrderId.isBlank()) {
            orderRepository.updateExternalOrderId(sentOrder.orderId(), exchangeOrderId, ackTime);
            acceptedSnapshot = sentOrder.withExternalOrderId(exchangeOrderId);
        }
        OrderRecord acceptedOrder = transitionOrderInternal(
                acceptedSnapshot,
                OrderStatus.ACCEPTED,
                "ORDER_ACKED_BY_ADAPTER",
                request.traceId()
        );
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                acceptedOrder.clientOrderId(),
                acceptedOrder.traceId(),
                new OrderAck(
                        acceptedOrder.accountId(),
                        acceptedOrder.venue(),
                        acceptedOrder.clientOrderId(),
                        exchangeOrderId,
                        acceptedOrder.status().name(),
                        ackTime
                )
        );
        auditLogRepository.append(
                "ORDER",
                "ORDER_ACKED",
                acceptedOrder.orderId(),
                request.traceId(),
                detail(
                        "exchange_code", acceptedOrder.venue(),
                        "exchange_order_id", exchangeOrderId,
                        "result_category", gatewayResult.resultCategory().name(),
                        "status", acceptedOrder.status().name()
                )
        );
        return new PlaceOrderResult(acceptedOrder.orderId(), acceptedOrder.status(), false);
    }

    /**
     * 在 adapter 延迟确认时只保留 SENT 状态并写审计。
     * Why:
     * `DEFERRED / REMOTE_UNAVAILABLE` 等类别不能假装本地已 ACCEPTED/REJECTED，
     * 最稳妥的做法是把订单停在 `SENT`，交由 query-confirm / recovery 继续确认。
     */
    @Transactional
    public PlaceOrderResult finalizeDeferredPlaceOrder(
            PlaceOrderRequest request,
            OrderRecord sentOrder,
            TradingPlaceGatewayResult gatewayResult
    ) {
        TradingGatewayFailure failure = gatewayResult.failure();
        auditLogRepository.append(
                "ORDER",
                "ORDER_ACK_DEFERRED",
                sentOrder.orderId(),
                request.traceId(),
                detail(
                        "exchange_code", sentOrder.venue(),
                        "request_id", request.requestId(),
                        "result_category", gatewayResult.resultCategory().name(),
                        "error_code", failure == null ? null : failure.code(),
                        "error_message", failure == null ? null : failure.message()
                )
        );
        return new PlaceOrderResult(sentOrder.orderId(), sentOrder.status(), false);
    }

    /**
     * 在 adapter 明确拒绝后写回本地拒绝事实。
     */
    @Transactional
    public PlaceOrderResult finalizeRejectedPlaceOrder(
            PlaceOrderRequest request,
            OrderRecord sentOrder,
            TradingPlaceGatewayResult gatewayResult,
            Instant ackTime
    ) {
        TradingGatewayFailure failure = gatewayResult.failure();
        String rejectCode = failure == null || failure.code() == null || failure.code().isBlank()
                ? "ORDER_REJECTED_BY_ADAPTER"
                : failure.code();
        String rejectReason = failure == null || failure.message() == null || failure.message().isBlank()
                ? "adapter rejected order"
                : failure.message();
        OrderRecord rejectedOrder = transitionOrderInternal(
                sentOrder,
                OrderStatus.REJECTED,
                rejectCode,
                request.traceId()
        );
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                rejectedOrder.clientOrderId(),
                rejectedOrder.traceId(),
                new OrderReject(
                        rejectedOrder.accountId(),
                        rejectedOrder.venue(),
                        rejectedOrder.clientOrderId(),
                        rejectCode,
                        rejectReason,
                        ackTime
                )
        );
        auditLogRepository.append(
                "ORDER",
                "ORDER_REJECTED",
                rejectedOrder.orderId(),
                request.traceId(),
                detail(
                        "exchange_code", rejectedOrder.venue(),
                        "result_category", gatewayResult.resultCategory().name(),
                        "reject_code", rejectCode,
                        "reject_reason", rejectReason
                )
        );
        return new PlaceOrderResult(rejectedOrder.orderId(), rejectedOrder.status(), false);
    }

    /**
     * 预写撤单请求的本地状态推进。
     * Why:
     * `CANCEL_REQUESTED` 与对应状态变更事件必须一并提交，
     * 否则会出现 orders 已推进、event_store 却缺关键事实的问题。
     */
    @Transactional
    public OrderRecord prepareCancelOrder(CancelOrderRequest request, OrderRecord currentOrder) {
        OrderRecord cancelRequestedOrder = transitionOrderInternal(
                currentOrder,
                OrderStatus.CANCEL_REQUESTED,
                request.reason(),
                request.traceId()
        );
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                cancelRequestedOrder.clientOrderId(),
                request.traceId(),
                new OrderStatusChangedPayload(
                        cancelRequestedOrder.orderId(),
                        cancelRequestedOrder.accountId(),
                        cancelRequestedOrder.clientOrderId(),
                        cancelRequestedOrder.status(),
                        "ORDER_CANCEL_REQUESTED",
                        Instant.now(clock)
                )
        );
        return cancelRequestedOrder;
    }

    /**
     * 在 adapter 接受撤单后写回本地取消终态。
     */
    @Transactional
    public CancelOrderResult finalizeAcceptedCancelOrder(
            CancelOrderRequest request,
            OrderRecord cancelRequestedOrder,
            Instant ackTime
    ) {
        OrderRecord cancelledOrder = transitionOrderInternal(
                cancelRequestedOrder,
                OrderStatus.CANCELLED,
                request.reason(),
                request.traceId()
        );
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                cancelledOrder.clientOrderId(),
                cancelledOrder.traceId(),
                new CancelAck(
                        cancelledOrder.accountId(),
                        cancelledOrder.venue(),
                        cancelledOrder.clientOrderId(),
                        cancelledOrder.externalOrderId(),
                        cancelledOrder.status().name(),
                        ackTime
                )
        );
        auditLogRepository.append(
                "ORDER",
                "ORDER_CANCELLED",
                cancelledOrder.orderId(),
                request.traceId(),
                detail(
                        "order_id", cancelledOrder.orderId(),
                        "status", cancelledOrder.status().name(),
                        "reason", request.reason(),
                        "venue", cancelledOrder.venue()
                )
        );
        return new CancelOrderResult(cancelledOrder.orderId(), cancelledOrder.status(), false);
    }

    /**
     * 在撤单结果未知时保留 `CANCEL_REQUESTED` 并写审计。
     */
    @Transactional
    public CancelOrderResult finalizeDeferredCancelOrder(
            CancelOrderRequest request,
            OrderRecord cancelRequestedOrder,
            TradingCancelGatewayResult gatewayResult
    ) {
        TradingGatewayFailure failure = gatewayResult.failure();
        auditLogRepository.append(
                "ORDER",
                "ORDER_CANCEL_ACK_DEFERRED",
                cancelRequestedOrder.orderId(),
                request.traceId(),
                detail(
                        "exchange_code", cancelRequestedOrder.venue(),
                        "result_category", gatewayResult.resultCategory().name(),
                        "error_code", failure == null ? null : failure.code(),
                        "error_message", failure == null ? null : failure.message()
                )
        );
        return new CancelOrderResult(cancelRequestedOrder.orderId(), cancelRequestedOrder.status(), false);
    }

    /**
     * 在 adapter 明确拒绝撤单后写回本地拒绝终态。
     */
    @Transactional
    public CancelOrderResult finalizeRejectedCancelOrder(
            CancelOrderRequest request,
            OrderRecord cancelRequestedOrder,
            TradingCancelGatewayResult gatewayResult,
            Instant ackTime
    ) {
        TradingGatewayFailure failure = gatewayResult.failure();
        String rejectCode = failure == null || failure.code() == null || failure.code().isBlank()
                ? "CANCEL_REJECTED_BY_ADAPTER"
                : failure.code();
        String rejectReason = failure == null || failure.message() == null || failure.message().isBlank()
                ? "adapter rejected cancel"
                : failure.message();
        OrderRecord cancelRejectedOrder = transitionOrderInternal(
                cancelRequestedOrder,
                OrderStatus.CANCEL_REJECTED,
                rejectCode,
                request.traceId()
        );
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                cancelRejectedOrder.clientOrderId(),
                cancelRejectedOrder.traceId(),
                new CancelReject(
                        cancelRejectedOrder.accountId(),
                        cancelRejectedOrder.venue(),
                        cancelRejectedOrder.clientOrderId(),
                        cancelRejectedOrder.externalOrderId(),
                        rejectCode,
                        rejectReason,
                        ackTime
                )
        );
        auditLogRepository.append(
                "ORDER",
                "ORDER_CANCEL_REJECTED",
                cancelRejectedOrder.orderId(),
                request.traceId(),
                detail(
                        "order_id", cancelRejectedOrder.orderId(),
                        "status", cancelRejectedOrder.status().name(),
                        "result_category", gatewayResult.resultCategory().name(),
                        "reject_code", rejectCode,
                        "reject_reason", rejectReason,
                        "exchange_code", cancelRejectedOrder.venue()
                )
        );
        return new CancelOrderResult(cancelRejectedOrder.orderId(), cancelRejectedOrder.status(), false);
    }

    /**
     * 对既有订单执行显式状态迁移。
     */
    @Transactional
    public OrderRecord transitionOrder(String orderId, OrderStatus nextStatus, String reason, String traceId) {
        OrderRecord currentOrder = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        return transitionOrderInternal(currentOrder, nextStatus, reason, traceId);
    }

    /**
     * 为既有订单补写外部订单号。
     */
    @Transactional
    public OrderRecord linkExternalOrderId(String orderId, String externalOrderId, String traceId) {
        OrderRecord currentOrder = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        if (externalOrderId.equals(currentOrder.externalOrderId())) {
            return currentOrder;
        }
        Instant now = Instant.now(clock);
        orderRepository.updateExternalOrderId(orderId, externalOrderId, now);
        auditLogRepository.append(
                "ORDER",
                "ORDER_EXTERNAL_ID_LINKED",
                orderId,
                traceId,
                detail("order_id", orderId, "external_order_id", externalOrderId, "venue", currentOrder.venue())
        );
        return currentOrder.withExternalOrderId(externalOrderId);
    }

    private OrderRecord transitionOrderInternal(
            OrderRecord currentOrder,
            OrderStatus nextStatus,
            String reason,
            String traceId
    ) {
        Instant now = Instant.now(clock);
        try {
            OrderStatus transitioned = orderStateMachine.transition(currentOrder.status(), nextStatus);
            orderRepository.updateStatus(currentOrder.orderId(), transitioned, reason, now);
            auditLogRepository.append(
                    "ORDER",
                    "ORDER_STATUS_TRANSITION",
                    currentOrder.orderId(),
                    traceId,
                    detail("from", currentOrder.status().name(), "to", transitioned.name(), "reason", reason)
            );
            return currentOrder.withStatus(transitioned, reason);
        } catch (IllegalStateException ex) {
            auditLogRepository.append(
                    "ORDER",
                    "ORDER_STATUS_TRANSITION_REJECTED",
                    currentOrder.orderId(),
                    traceId,
                    detail(
                            "from", currentOrder.status().name(),
                            "to", nextStatus.name(),
                            "reason", reason,
                            "error", ex.getMessage()
                    )
            );
            throw ex;
        }
    }

    private void publishEvent(String topic, String key, String traceId, Object payload) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                payload.getClass().getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                traceId,
                key,
                payload
        );
        eventPublisherPort.append(topic, envelope);
    }

    private Map<String, Object> detail(Object... fields) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            detail.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return detail;
    }

    /**
     * PlaceOrderPreparation 描述本地下单写阶段的结果。
     */
    public record PlaceOrderPreparation(
            OrderRecord sentOrder,
            PlaceOrderResult completedResult
    ) {
        static PlaceOrderPreparation readyForAdapter(OrderRecord sentOrder) {
            return new PlaceOrderPreparation(sentOrder, null);
        }

        static PlaceOrderPreparation completed(PlaceOrderResult completedResult) {
            return new PlaceOrderPreparation(null, completedResult);
        }
    }
}


