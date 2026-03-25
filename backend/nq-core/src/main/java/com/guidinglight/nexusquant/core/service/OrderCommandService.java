package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.contracts.command.CancelOrderCommand;
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
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.core.execution.AdapterRouter;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.core.service.port.OrderRepository;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.core.state.OrderStateMachine;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;
import com.guidinglight.nexusquant.risk.service.RiskGate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * OrderCommandService 负责 GateD 的统一下单/撤单编排。
 * <p>
 * Why:
 * GateD 需要把 place / cancel 的入口继续保留在一个应用服务内，但也必须避免它重新长成“什么都做”的巨石。
 * 因此本类只负责执行编排、风控调用、adapter 路由、event_store 与审计写入；
 * 生命周期语义动作统一收口到 `OrderLifecycleService`，contracts 组装统一收口到 `ExecutionCommandMapper`。
 */
@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);
    private static final String SOURCE = "nq-core.order-command-service";

    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final RiskGate riskGate;
    private final AuditLogRepository auditLogRepository;
    private final RiskEventRepository riskEventRepository;
    private final EventPublisherPort eventPublisherPort;
    private final AdapterRouter adapterRouter;
    private final Clock clock;

    /**
     * @param orderRepository     订单仓储端口
     * @param orderStateMachine   订单状态机
     * @param riskGate            风控服务
     * @param auditLogRepository  审计仓储
     * @param riskEventRepository 风控事件仓储
     * @param eventPublisherPort  事件事实链追加端口
     * @param adapterRouter       adapter 路由器
     */
    public OrderCommandService(
            OrderRepository orderRepository,
            OrderStateMachine orderStateMachine,
            RiskGate riskGate,
            AuditLogRepository auditLogRepository,
            RiskEventRepository riskEventRepository,
            EventPublisherPort eventPublisherPort,
            AdapterRouter adapterRouter
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.orderStateMachine = Objects.requireNonNull(orderStateMachine, "orderStateMachine must not be null");
        this.riskGate = Objects.requireNonNull(riskGate, "riskGate must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.riskEventRepository = Objects.requireNonNull(riskEventRepository, "riskEventRepository must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.adapterRouter = Objects.requireNonNull(adapterRouter, "adapterRouter must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 执行下单编排。
     * <p>
     * Why:
     * 该方法是“命令 -> 风控 -> 状态机 -> TradingAdapter -> 回执事件化”的唯一入口，
     * 既要保证 account_id + client_order_id 幂等，又要确保外部回执总能沉淀到 event_store。
     *
     * @param request 下单请求
     * @return 下单结果，包含订单状态与是否命中幂等
     */
    public PlaceOrderResult placeOrder(PlaceOrderRequest request) {
        validateRequest(request);
        Instant now = Instant.now(clock);
        Optional<OrderRecord> existingOrder = orderRepository.findByAccountAndClientOrderId(
                request.accountId(),
                request.clientOrderId()
        );
        String candidateOrderId = existingOrder.map(OrderRecord::orderId).orElseGet(this::generateOrderId);
        PlaceOrderCommand command = ExecutionCommandMapper.toPlaceCommand(request, candidateOrderId);
        publishEvent(TopicNames.ORDER_COMMAND_V1, request.clientOrderId(), request.traceId(), command);

        if (existingOrder.isPresent()) {
            OrderRecord order = existingOrder.get();
            auditLogRepository.append(
                    "ORDER",
                    "PLACE_ORDER_IDEMPOTENT_HIT",
                    order.orderId(),
                    request.traceId(),
                    detail(
                            "account_id", order.accountId(),
                            "client_order_id", order.clientOrderId(),
                            "request_id", request.requestId(),
                            "idempotency_key", request.idempotencyKey(),
                            "status", order.status().name(),
                            "venue", order.venue()
                    )
            );
            return new PlaceOrderResult(order.orderId(), order.status(), true);
        }

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
                return new PlaceOrderResult(order.orderId(), order.status(), true);
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
            OrderRecord rejectedOrder = transitionOrder(
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
            return new PlaceOrderResult(rejectedOrder.orderId(), rejectedOrder.status(), false);
        }

        OrderRecord riskPassedOrder = transitionOrder(
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

        OrderRecord sentOrder = transitionOrder(
                riskPassedOrder,
                OrderStatus.SENT,
                "ORDER_ROUTED_TO_ADAPTER",
                request.traceId()
        );
        TradingAdapter tradingAdapter = adapterRouter.route(sentOrder.accountId(), sentOrder.venue()).trading();
        AdapterOrderAck adapterAck = invokePlaceOrder(tradingAdapter, request, sentOrder);
        Instant ackTime = adapterAck.ackTs() == null ? Instant.now(clock) : adapterAck.ackTs();

        if (adapterAck.accepted()) {
            orderRepository.updateExternalOrderId(sentOrder.orderId(), adapterAck.exchangeOrderId(), ackTime);
            OrderRecord acceptedSnapshot = sentOrder.withExternalOrderId(adapterAck.exchangeOrderId());
            OrderRecord acceptedOrder = transitionOrder(
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
                            adapterAck.exchangeOrderId(),
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
                            "exchange_order_id", adapterAck.exchangeOrderId(),
                            "result_category", adapterAck.resultCategory().name(),
                            "status", acceptedOrder.status().name()
                    )
            );
            return new PlaceOrderResult(acceptedOrder.orderId(), acceptedOrder.status(), false);
        }

        AdapterError error = adapterAck.error();
        if (shouldDeferOrderRejection(error)) {
            auditLogRepository.append(
                    "ORDER",
                    "ORDER_ACK_DEFERRED",
                    sentOrder.orderId(),
                    request.traceId(),
                    detail(
                            "exchange_code", sentOrder.venue(),
                            "request_id", request.requestId(),
                            "result_category", adapterAck.resultCategory().name(),
                            "error_code", error == null ? null : error.code(),
                            "error_message", error == null ? null : error.message()
                    )
            );
            return new PlaceOrderResult(sentOrder.orderId(), sentOrder.status(), false);
        }
        String rejectCode = error == null || error.code() == null || error.code().isBlank()
                ? "ORDER_REJECTED_BY_ADAPTER"
                : error.code();
        String rejectReason = error == null || error.message() == null || error.message().isBlank()
                ? "adapter rejected order"
                : error.message();
        OrderRecord rejectedOrder = transitionOrder(
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
                        "result_category", adapterAck.resultCategory().name(),
                        "reject_code", rejectCode,
                        "reject_reason", rejectReason
                )
        );
        return new PlaceOrderResult(rejectedOrder.orderId(), rejectedOrder.status(), false);
    }

    /**
     * 执行撤单编排。
     * <p>
     * Why:
     * 撤单也必须先过状态机，再通过 TradingAdapter 触发外部动作，最后把外部结果事件化，
     * 否则 cancel 成功/失败无法在 event_store 中重建完整证据链。
     *
     * @param request 撤单请求
     * @return 撤单结果，包含订单状态与是否命中幂等
     */
    public CancelOrderResult cancelOrder(CancelOrderRequest request) {
        validateCancelRequest(request);
        OrderRecord currentOrder = resolveCancelTarget(request);
        logCancelPath("order_cancel_path_entered", currentOrder, request.traceId());
        CancelOrderCommand command = ExecutionCommandMapper.toCancelCommand(request, currentOrder);
        publishEvent(TopicNames.ORDER_COMMAND_V1, currentOrder.clientOrderId(), request.traceId(), command);
        if (currentOrder.status() == OrderStatus.CANCELLED) {
            logCancelPath("order_cancel_short_circuit_already_cancelled", currentOrder, request.traceId());
            auditLogRepository.append(
                    "ORDER",
                    "CANCEL_ORDER_IDEMPOTENT_HIT",
                    currentOrder.orderId(),
                    request.traceId(),
                    detail("order_id", currentOrder.orderId(), "status", currentOrder.status().name(), "reason", request.reason())
            );
            return new CancelOrderResult(currentOrder.orderId(), currentOrder.status(), true);
        }

        OrderRecord cancelRequestedOrder = transitionOrder(
                currentOrder,
                OrderStatus.CANCEL_REQUESTED,
                request.reason(),
                request.traceId()
        );
        publishOrderStatusChanged(cancelRequestedOrder, request.traceId(), "ORDER_CANCEL_REQUESTED");

        logCancelPath("order_cancel_before_adapter_call", cancelRequestedOrder, request.traceId());
        TradingAdapter tradingAdapter = adapterRouter.route(cancelRequestedOrder.accountId(), cancelRequestedOrder.venue()).trading();
        AdapterCancelAck cancelAck = invokeCancelOrder(tradingAdapter, request, cancelRequestedOrder);
        log.info(
                "order_cancel_after_adapter_call orderId={} clientOrderId={} externalOrderId={} accountId={} currentStatus={} traceId={} venue={} adapterAccepted={}",
                cancelRequestedOrder.orderId(),
                cancelRequestedOrder.clientOrderId(),
                cancelRequestedOrder.externalOrderId(),
                cancelRequestedOrder.accountId(),
                cancelRequestedOrder.status().name(),
                request.traceId(),
                cancelRequestedOrder.venue(),
                cancelAck.accepted()
        );
        Instant ackTime = cancelAck.ts() == null ? Instant.now(clock) : cancelAck.ts();
        if (cancelAck.accepted()) {
            OrderRecord cancelledOrder = transitionOrder(
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

        AdapterError error = cancelAck.error();
        if (shouldDeferOrderRejection(error)) {
            auditLogRepository.append(
                    "ORDER",
                    "ORDER_CANCEL_ACK_DEFERRED",
                    cancelRequestedOrder.orderId(),
                    request.traceId(),
                    detail(
                            "exchange_code", cancelRequestedOrder.venue(),
                            "result_category", cancelAck.resultCategory().name(),
                            "error_code", error == null ? null : error.code(),
                            "error_message", error == null ? null : error.message()
                    )
            );
            return new CancelOrderResult(cancelRequestedOrder.orderId(), cancelRequestedOrder.status(), false);
        }
        String rejectCode = error == null || error.code() == null || error.code().isBlank()
                ? "CANCEL_REJECTED_BY_ADAPTER"
                : error.code();
        String rejectReason = error == null || error.message() == null || error.message().isBlank()
                ? "adapter rejected cancel"
                : error.message();
        OrderRecord cancelRejectedOrder = transitionOrder(
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
                        "result_category", cancelAck.resultCategory().name(),
                        "reject_code", rejectCode,
                        "reject_reason", rejectReason,
                        "exchange_code", cancelRejectedOrder.venue()
                )
        );
        return new CancelOrderResult(cancelRejectedOrder.orderId(), cancelRejectedOrder.status(), false);
    }

    private void logCancelPath(String eventName, OrderRecord order, String traceId) {
        log.info(
                "{} orderId={} clientOrderId={} externalOrderId={} accountId={} currentStatus={} traceId={} venue={}",
                eventName,
                order.orderId(),
                order.clientOrderId(),
                order.externalOrderId(),
                order.accountId(),
                order.status().name(),
                traceId,
                order.venue()
        );
    }

    /**
     * 执行显式状态迁移。
     * <p>
     * Why:
     * 统一迁移 API 可以保证所有调用方都经过状态机检查，杜绝直接 setStatus 的旁路写入。
     *
     * @param orderId    订单 ID
     * @param nextStatus 目标状态
     * @param reason     迁移原因
     * @param traceId    链路追踪 ID
     * @return 迁移后的订单快照
     */
    OrderRecord transitionOrder(String orderId, OrderStatus nextStatus, String reason, String traceId) {
        OrderRecord currentOrder = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        return transitionOrder(currentOrder, nextStatus, reason, traceId);
    }

    /**
     * 查询指定状态订单，供 scheduler/恢复流程使用。
     */
    public List<OrderRecord> findOrdersByStatuses(Collection<OrderStatus> statuses, int limit) {
        return orderRepository.findByStatuses(statuses, limit);
    }

    /**
     * 按 ID 查询订单。
     */
    public Optional<OrderRecord> findByOrderId(String orderId) {
        return orderRepository.findByOrderId(orderId);
    }

    /**
     * 按账户与 client_order_id 查询订单，供上游触发器做幂等短路。
     */
    public Optional<OrderRecord> findByAccountAndClientOrderId(Long accountId, String clientOrderId) {
        return orderRepository.findByAccountAndClientOrderId(accountId, clientOrderId);
    }

    /**
     * 为已存在订单补写 external_order_id。
     * <p>
     * Why:
     * GateC-1 的 query-confirm 与恢复流程可能在初始回执后才确认 ordId，
     * 这里统一通过 core 落库，避免 scheduler 直接写 orders 破坏审计口径。
     *
     * @param orderId         系统订单 ID
     * @param externalOrderId 外部订单号
     * @param traceId         链路追踪 ID
     * @return 更新后的订单快照
     */
    public OrderRecord linkExternalOrderId(String orderId, String externalOrderId, String traceId) {
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalArgumentException("externalOrderId must not be blank");
        }
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

    private OrderRecord transitionOrder(OrderRecord currentOrder, OrderStatus nextStatus, String reason, String traceId) {
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

    private AdapterOrderAck invokePlaceOrder(TradingAdapter tradingAdapter, PlaceOrderRequest request, OrderRecord order) {
        try {
            return tradingAdapter.placeOrder(new AdapterOrderRequest(
                    request.requestId(),
                    order.orderId(),
                    order.accountId(),
                    order.venue(),
                    order.symbol(),
                    order.clientOrderId(),
                    request.idempotencyKey(),
                    order.side(),
                    order.type(),
                    order.price(),
                    order.qty(),
                    null,
                    request.timeInForce(),
                    request.source(),
                    order.strategyRunId(),
                    request.traceId()
            ));
        } catch (RuntimeException ex) {
            auditLogRepository.append(
                    "ORDER",
                    "ADAPTER_PLACE_ORDER_FAILED",
                    order.orderId(),
                    request.traceId(),
                    detail("venue", order.venue(), "error", ex.getMessage())
            );
            return new AdapterOrderAck(
                    false,
                    order.venue(),
                    order.accountId(),
                    order.symbol(),
                    order.clientOrderId(),
                    null,
                    "REJECTED",
                    AdapterResultCategory.REMOTE_UNAVAILABLE,
                    new AdapterError("ADAPTER_CALL_FAILED", ex.getMessage(), AdapterResultCategory.REMOTE_UNAVAILABLE, true),
                    Instant.now(clock),
                    null,
                    request.traceId(),
                    null
            );
        }
    }

    private AdapterCancelAck invokeCancelOrder(TradingAdapter tradingAdapter, CancelOrderRequest request, OrderRecord order) {
        try {
            return tradingAdapter.cancelOrder(new AdapterCancelRequest(
                    request.requestId(),
                    order.orderId(),
                    order.accountId(),
                    order.venue(),
                    order.symbol(),
                    order.clientOrderId(),
                    order.externalOrderId(),
                    request.reason(),
                    request.traceId()
            ));
        } catch (RuntimeException ex) {
            auditLogRepository.append(
                    "ORDER",
                    "ADAPTER_CANCEL_ORDER_FAILED",
                    order.orderId(),
                    request.traceId(),
                    detail("venue", order.venue(), "error", ex.getMessage())
            );
            return new AdapterCancelAck(
                    false,
                    order.venue(),
                    order.externalOrderId(),
                    AdapterResultCategory.REMOTE_UNAVAILABLE,
                    new AdapterError("ADAPTER_CALL_FAILED", ex.getMessage(), AdapterResultCategory.REMOTE_UNAVAILABLE, true),
                    Instant.now(clock),
                    request.traceId(),
                    null
            );
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

    private void validateRequest(PlaceOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.requestId() == null || request.requestId().isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (request.accountId() == null || request.accountId() <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        if (request.venue() == null || request.venue().isBlank()) {
            throw new IllegalArgumentException("venue must not be blank");
        }
        if (request.clientOrderId() == null || request.clientOrderId().isBlank()) {
            throw new IllegalArgumentException("clientOrderId must not be blank");
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (request.side() == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (request.source() == null || request.source().isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (request.traceId() == null || request.traceId().isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (request.type() == OrderType.LIMIT
                && (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("price must be positive for LIMIT order");
        }
    }

    private void validateCancelRequest(CancelOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.requestId() == null || request.requestId().isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        boolean hasOrderId = request.orderId() != null && !request.orderId().isBlank();
        if (!hasOrderId) {
            if (request.accountId() == null || request.accountId() <= 0) {
                throw new IllegalArgumentException("accountId must be positive when orderId is absent");
            }
            if (request.clientOrderId() == null || request.clientOrderId().isBlank()) {
                throw new IllegalArgumentException("clientOrderId must not be blank when orderId is absent");
            }
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (request.traceId() == null || request.traceId().isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    private OrderRecord resolveCancelTarget(CancelOrderRequest request) {
        OrderRecord target;
        if (request.orderId() != null && !request.orderId().isBlank()) {
            target = orderRepository.findByOrderId(request.orderId())
                    .orElseThrow(() -> new IllegalArgumentException("order not found: " + request.orderId()));
        } else {
            target = orderRepository.findByAccountAndClientOrderId(request.accountId(), request.clientOrderId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "order not found by accountId/clientOrderId: "
                                    + request.accountId() + "/" + request.clientOrderId()
                    ));
        }
        validateCancelTargetSemantics(request, target);
        return target;
    }

    private void validateCancelTargetSemantics(CancelOrderRequest request, OrderRecord target) {
        // Why: GateD 要求撤单契约中的 venue / symbol / externalOrderId 具备真实语义，不能只是“可选摆设”。
        if (request.accountId() != null && !request.accountId().equals(target.accountId())) {
            throw new IllegalArgumentException("accountId does not match cancel target");
        }
        if (request.venue() != null && !request.venue().equalsIgnoreCase(target.venue())) {
            throw new IllegalArgumentException("venue does not match cancel target");
        }
        if (request.symbol() != null && !request.symbol().equalsIgnoreCase(target.symbol())) {
            throw new IllegalArgumentException("symbol does not match cancel target");
        }
        if (request.externalOrderId() != null
                && target.externalOrderId() != null
                && !request.externalOrderId().equals(target.externalOrderId())) {
            throw new IllegalArgumentException("externalOrderId does not match cancel target");
        }
    }

    private void publishOrderStatusChanged(OrderRecord order, String traceId, String reason) {
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                order.clientOrderId(),
                traceId,
                new OrderStatusChangedPayload(
                        order.orderId(),
                        order.accountId(),
                        order.clientOrderId(),
                        order.status(),
                        reason,
                        Instant.now(clock)
                )
        );
    }

    private String generateOrderId() {
        return "ord-" + UUID.randomUUID();
    }

    private Map<String, Object> detail(Object... fields) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            detail.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return detail;
    }

    private boolean shouldDeferOrderRejection(AdapterError error) {
        if (error == null || error.category() == null) {
            return false;
        }
        return switch (error.category()) {
            case DEFERRED, RETRYABLE_FAILURE, THROTTLED, REMOTE_UNAVAILABLE -> true;
            default -> false;
        };
    }
}
