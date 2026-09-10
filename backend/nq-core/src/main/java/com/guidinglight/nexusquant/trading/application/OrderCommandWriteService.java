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
import com.guidinglight.nexusquant.trading.domain.TradingVenue;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrdinaryPlaceAuthorityRepository;
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
import java.math.BigDecimal;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

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
    private final OrdinaryPlaceAuthorityRepository placeAuthorities;
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
            EventPublisherPort eventPublisherPort,
            OrdinaryPlaceAuthorityRepository placeAuthorities
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.orderStateMachine = Objects.requireNonNull(orderStateMachine, "orderStateMachine must not be null");
        this.riskGate = Objects.requireNonNull(riskGate, "riskGate must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.riskEventRepository = Objects.requireNonNull(riskEventRepository, "riskEventRepository must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.clock = Clock.systemUTC();
        this.placeAuthorities = Objects.requireNonNull(placeAuthorities, "placeAuthorities must not be null");
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
                request.traceId(),
                request.tradeEnv()
        );

        try {
            if (createdOrder.canonicalVenue() == TradingVenue.OKX) placeAuthorities.insertOrder(createdOrder, now);
            else orderRepository.insert(createdOrder, now);
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
     * identity 补充与当前代际的迁移事实在一个事务内完成；旧代际回执仅允许单调补充 identity 并写审计。
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
            linkExternalOrderId(sentOrder.orderId(), exchangeOrderId, request.traceId());
            acceptedSnapshot = sentOrder.withExternalOrderId(exchangeOrderId);
        }
        TransitionResult transition = transitionOrderAttempt(
                acceptedSnapshot,
                OrderStatus.ACCEPTED,
                "ORDER_ACKED_BY_ADAPTER",
                request.traceId()
        );
        OrderRecord acceptedOrder = transition.order();
        if (!transition.applied()) {
            recordStaleProviderResult(sentOrder, acceptedOrder, "PLACE_ACCEPTED", request.requestId(), request.traceId());
            return new PlaceOrderResult(acceptedOrder.orderId(), acceptedOrder.status(), false);
        }
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
     * 在 adapter 延迟确认时观察当前 durable truth 并写审计，不推进旧代际状态。
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
        OrderRecord durable = loadOrder(sentOrder.orderId());
        if (durable.version() != sentOrder.version()) {
            recordStaleProviderResult(sentOrder, durable, "PLACE_DEFERRED", request.requestId(), request.traceId());
            return new PlaceOrderResult(durable.orderId(), durable.status(), false);
        }
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
        TransitionResult transition = transitionOrderAttempt(
                sentOrder,
                OrderStatus.REJECTED,
                rejectCode,
                request.traceId()
        );
        OrderRecord rejectedOrder = transition.order();
        if (!transition.applied()) {
            recordStaleProviderResult(sentOrder, rejectedOrder, "PLACE_REJECTED", request.requestId(), request.traceId());
            return new PlaceOrderResult(rejectedOrder.orderId(), rejectedOrder.status(), false);
        }
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
     * @throws IllegalStateException 旧快照或非法迁移导致准备失败；调用方不得继续调用 gateway
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
        TransitionResult transition = transitionOrderAttempt(
                cancelRequestedOrder,
                OrderStatus.CANCELLED,
                request.reason(),
                request.traceId()
        );
        OrderRecord cancelledOrder = transition.order();
        if (!transition.applied()) {
            recordStaleProviderResult(cancelRequestedOrder, cancelledOrder, "CANCEL_ACCEPTED", request.requestId(), request.traceId());
            return new CancelOrderResult(cancelledOrder.orderId(), cancelledOrder.status(), false);
        }
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
     * 在撤单结果未知时观察当前 durable truth 并写审计，不推进旧代际状态。
     */
    @Transactional
    public CancelOrderResult finalizeDeferredCancelOrder(
            CancelOrderRequest request,
            OrderRecord cancelRequestedOrder,
            TradingCancelGatewayResult gatewayResult
    ) {
        OrderRecord durable = loadOrder(cancelRequestedOrder.orderId());
        if (durable.version() != cancelRequestedOrder.version()) {
            recordStaleProviderResult(cancelRequestedOrder, durable, "CANCEL_DEFERRED", request.requestId(), request.traceId());
            return new CancelOrderResult(durable.orderId(), durable.status(), false);
        }
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
        TransitionResult transition = transitionOrderAttempt(
                cancelRequestedOrder,
                OrderStatus.CANCEL_REJECTED,
                rejectCode,
                request.traceId()
        );
        OrderRecord cancelRejectedOrder = transition.order();
        if (!transition.applied()) {
            recordStaleProviderResult(cancelRequestedOrder, cancelRejectedOrder, "CANCEL_REJECTED", request.requestId(), request.traceId());
            return new CancelOrderResult(cancelRejectedOrder.orderId(), cancelRejectedOrder.status(), false);
        }
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
     * 对既有订单执行显式状态迁移；并发冲突时返回 durable truth，不刷新旧意图重试。
     */
    @Transactional
    public OrderRecord transitionOrder(String orderId, OrderStatus nextStatus, String reason, String traceId) {
        OrderRecord currentOrder = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        return transitionOrderAttempt(currentOrder, nextStatus, reason, traceId).order();
    }

    /** 返回到非事务编排器前必须已确认提交；异常/提交未知不返回一次性发送许可。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean armOrdinaryPlace(OrderRecord expected) {
        boolean granted = placeAuthorities.arm(expected);
        auditLogRepository.append("ORDER", granted ? "PLACE_MAY_HAVE_ESCAPED" : "PLACE_AUTHORITY_NOT_GRANTED",
                expected.orderId(), expected.traceId(), detail("expected_version", expected.version()));
        return granted;
    }

    /** 负面查询仅是观察；撤销与两个合法状态迁移必须在同一提交边界完成。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeOrdinaryNoOrder(String orderId, String traceId) {
        OrderRecord current = loadOrder(orderId);
        if (current.canonicalVenue() != TradingVenue.OKX || !Set.of(OrderStatus.SENT, OrderStatus.ACCEPTED,
                OrderStatus.PARTIALLY_FILLED, OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCEL_REJECTED)
                .contains(current.status())) return false;
        if (!placeAuthorities.revoke(current)) {
            auditLogRepository.append("RECOVERY", "MANUAL_RESOLUTION_REQUIRED", orderId, traceId,
                    detail("reason", "PLACE_MAY_HAVE_ESCAPED_OR_AUTHORITY_MISSING", "status", current.status().name()));
            return false;
        }
        String reason = "ORDER_NOT_FOUND/OKX_51603";
        if (current.status() != OrderStatus.CANCEL_REQUESTED) {
            current = transitionOrderInternal(current, OrderStatus.CANCEL_REQUESTED, reason, traceId);
            publishEvent(TopicNames.ORDER_EVENT_V1, current.clientOrderId(), traceId,
                    new OrderStatusChangedPayload(orderId, current.accountId(), current.clientOrderId(),
                            current.status(), reason, Instant.now(clock)));
        }
        current = transitionOrderInternal(current, OrderStatus.CANCELLED, reason, traceId);
        publishEvent(TopicNames.ORDER_EVENT_V1, current.clientOrderId(), traceId,
                new OrderStatusChangedPayload(orderId, current.accountId(), current.clientOrderId(),
                        current.status(), reason, Instant.now(clock)));
        return true;
    }

    /**
     * 仅对账可使用的强成交事实纠正；普通状态机仍禁止 CANCELLED → FILLED。
     * 冲突后重新读取状态与完整 durable fills，不能把旧 ACK 或旧证明换新 version 重试。
     */
    @Transactional
    public OrderRecord reconcileCancelledExecution(String orderId, String traceId) {
        final String reason = "RECONCILE_FULL_EXECUTION_PROVEN";
        for (int attempt = 0; attempt < 3; attempt++) {
            OrderRecord current = loadOrder(orderId);
            if (current.status() != OrderStatus.CANCELLED && current.status() != OrderStatus.FILLED) return current;
            if (current.externalOrderId() == null || current.externalOrderId().isBlank()) return current;
            BigDecimal executed = Objects.requireNonNull(orderRepository.durableExecutedQuantity(orderId));
            int comparison = executed.compareTo(current.qty());
            if (executed.signum() < 0 || comparison > 0) {
                throw new IllegalStateException("RECONCILIATION_OVERFILL: " + orderId);
            }
            if (comparison < 0 || current.status() == OrderStatus.FILLED) return current;
            int affected = orderRepository.compareAndSetCancelledToFilled(orderId, current.version(), reason, Instant.now(clock));
            if (affected == 0) {
                auditLogRepository.append("RECONCILE", "ORDER_TERMINAL_CORRECTION_STALE", orderId, traceId,
                        detail("expected_version", current.version(), "executed_qty", executed));
                continue;
            }
            if (affected != 1) throw new IllegalStateException("invalid terminal correction affected rows: " + affected);
            OrderRecord corrected = current.withStatus(OrderStatus.FILLED, reason);
            auditLogRepository.append("RECONCILE", "ORDER_TERMINAL_EXECUTION_CORRECTED", orderId, traceId,
                    detail("from", "CANCELLED", "to", "FILLED", "expected_version", current.version(),
                            "version", corrected.version(), "original_qty", current.qty(), "executed_qty", executed,
                            "remaining_qty", current.qty().subtract(executed), "external_order_id", current.externalOrderId()));
            publishEvent(TopicNames.ORDER_EVENT_V1, current.clientOrderId(), traceId,
                    new OrderStatusChangedPayload(orderId, current.accountId(), current.clientOrderId(),
                            OrderStatus.FILLED, reason, Instant.now(clock)));
            return corrected;
        }
        throw new IllegalStateException("terminal correction contention: " + orderId);
    }

    /**
     * 为既有订单原子填充外部订单号；同号幂等，不同非空 identity 冲突时回滚。
     */
    @Transactional
    public OrderRecord linkExternalOrderId(String orderId, String externalOrderId, String traceId) {
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalArgumentException("externalOrderId must not be blank");
        }
        Instant now = Instant.now(clock);
        int affected = orderRepository.updateExternalOrderId(orderId, externalOrderId, now);
        if (affected < 0 || affected > 1) {
            throw new IllegalStateException("invalid order identity affected rows: " + affected);
        }
        OrderRecord currentOrder = loadOrder(orderId);
        if (!externalOrderId.equals(currentOrder.externalOrderId())) {
            throw new IllegalStateException("order external identity conflict: " + orderId);
        }
        if (affected == 1) {
            auditLogRepository.append(
                    "ORDER",
                    "ORDER_EXTERNAL_ID_LINKED",
                    orderId,
                    traceId,
                    detail("order_id", orderId, "external_order_id", externalOrderId, "venue", currentOrder.venue())
            );
        }
        return currentOrder;
    }

    private OrderRecord transitionOrderInternal(
            OrderRecord currentOrder, OrderStatus nextStatus, String reason, String traceId
    ) {
        TransitionResult result = transitionOrderAttempt(currentOrder, nextStatus, reason, traceId);
        // 本地准备未取得代际所有权时必须终止编排，禁止据旧快照发出新的外部动作。
        if (!result.applied()) {
            throw new IllegalStateException("stale order preparation: " + currentOrder.orderId());
        }
        return result.order();
    }

    /** 语义检查与数据库代际检查分层；冲突后只观察 durable truth，绝不以新代际重试旧意图。 */
    private TransitionResult transitionOrderAttempt(
            OrderRecord currentOrder,
            OrderStatus nextStatus,
            String reason,
            String traceId
    ) {
        Instant now = Instant.now(clock);
        try {
            OrderStatus transitioned = orderStateMachine.transition(currentOrder.status(), nextStatus);
            if (currentOrder.version() == Long.MAX_VALUE) {
                throw new IllegalStateException("order version exhausted");
            }
            int affected = orderRepository.compareAndSetStatus(currentOrder.orderId(), currentOrder.status(),
                    currentOrder.version(), transitioned, reason, now);
            if (affected == 0) {
                OrderRecord durable = loadOrder(currentOrder.orderId());
                if (durable.version() <= currentOrder.version()) {
                    throw new IllegalStateException("order generation invariant violated: " + currentOrder.orderId());
                }
                auditLogRepository.append("ORDER", "ORDER_STATUS_TRANSITION_STALE", currentOrder.orderId(), traceId,
                        detail("expected_version", currentOrder.version(), "durable_version", durable.version(),
                                "expected_status", currentOrder.status().name(), "durable_status", durable.status().name(),
                                "target_status", nextStatus.name()));
                return new TransitionResult(durable, false);
            }
            if (affected != 1) {
                throw new IllegalStateException("invalid order transition affected rows: " + affected);
            }
            auditLogRepository.append(
                    "ORDER",
                    "ORDER_STATUS_TRANSITION",
                    currentOrder.orderId(),
                    traceId,
                    detail("from", currentOrder.status().name(), "to", transitioned.name(), "reason", reason,
                            "expected_version", currentOrder.version(), "version", currentOrder.version() + 1)
            );
            return new TransitionResult(currentOrder.withStatus(transitioned, reason), true);
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

    private OrderRecord loadOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("order missing: " + orderId));
    }

    /** 旧回执仅落有代际上下文的审计，不复用无法表达回执所属代际的现有事件。 */
    private void recordStaleProviderResult(OrderRecord expected, OrderRecord durable, String providerResult,
            String requestId, String traceId) {
        auditLogRepository.append("ORDER", "STALE_PROVIDER_RESULT_IGNORED", expected.orderId(), traceId,
                detail("expected_version", expected.version(), "durable_version", durable.version(),
                        "expected_status", expected.status().name(), "durable_status", durable.status().name(),
                        "provider_result", providerResult, "request_id", requestId));
    }

    private record TransitionResult(OrderRecord order, boolean applied) { }

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


