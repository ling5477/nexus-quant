package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.contracts.command.CancelOrderCommand;
import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import com.guidinglight.nexusquant.contracts.event.CancelAck;
import com.guidinglight.nexusquant.contracts.event.CancelReject;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
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
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
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

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * OrderCommandService 负责 GateC-0 的统一下单/撤单编排。
 * <p>
 * Why:
 * 执行链路必须同时满足幂等、状态机、event_store、审计与 adapter 路由五类约束。
 * 如果 place/cancel 仍然保留 paper 专用分支，GateC-1 接真实交易所时就会被迫重写 core，
 * 因此这里先把链路收敛为“命令 -> 风控 -> AdapterRouter -> 回执事件化”。
 */
@Service
public class OrderCommandService {

    private static final String SOURCE = "nq-core.order-command-service";

    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final RiskGate riskGate;
    private final AuditLogRepository auditLogRepository;
    private final RiskEventRepository riskEventRepository;
    private final EventStoreAppender eventStoreAppender;
    private final AdapterRouter adapterRouter;
    private final Clock clock;

    /**
     * @param orderRepository     订单仓储端口
     * @param orderStateMachine   订单状态机
     * @param riskGate            风控服务
     * @param auditLogRepository  审计仓储
     * @param riskEventRepository 风控事件仓储
     * @param eventStoreAppender  event_store 写入器
     * @param adapterRouter       adapter 路由器
     */
    public OrderCommandService(
            OrderRepository orderRepository,
            OrderStateMachine orderStateMachine,
            RiskGate riskGate,
            AuditLogRepository auditLogRepository,
            RiskEventRepository riskEventRepository,
            EventStoreAppender eventStoreAppender,
            AdapterRouter adapterRouter
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.orderStateMachine = Objects.requireNonNull(orderStateMachine, "orderStateMachine must not be null");
        this.riskGate = Objects.requireNonNull(riskGate, "riskGate must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.riskEventRepository = Objects.requireNonNull(riskEventRepository, "riskEventRepository must not be null");
        this.eventStoreAppender = Objects.requireNonNull(eventStoreAppender, "eventStoreAppender must not be null");
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
        PlaceOrderCommand command = toCommand(request, candidateOrderId);
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
                request.qty(),
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
                        "venue", createdOrder.venue()
                )
        );

        RiskDecisionResult riskDecision = riskGate.evaluate(new RiskContext(command, now, request.traceId()));
        riskEventRepository.append(
                "ORDER",
                createdOrder.orderId(),
                riskDecision.decision(),
                riskDecision.reasonCode(),
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
                        riskDecision.reasonCode(),
                        riskDecision.severity().name(),
                        now
                )
        );

        if (riskDecision.decision() == RiskDecision.REJECT) {
            OrderRecord rejectedOrder = transitionOrder(
                    createdOrder,
                    OrderStatus.RISK_REJECTED,
                    riskDecision.reasonCode(),
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
                            riskDecision.reasonCode(),
                            riskDecision.severity().name(),
                            now
                    )
            );
            auditLogRepository.append(
                    "ORDER",
                    "RISK_REJECTED",
                    rejectedOrder.orderId(),
                    request.traceId(),
                    detail("reason", riskDecision.reasonCode(), "venue", rejectedOrder.venue())
            );
            return new PlaceOrderResult(rejectedOrder.orderId(), rejectedOrder.status(), false);
        }

        OrderRecord riskPassedOrder = transitionOrder(
                createdOrder,
                OrderStatus.RISK_PASSED,
                riskDecision.reasonCode(),
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
                        riskDecision.reasonCode(),
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
        AdapterOrderAck adapterAck = invokePlaceOrder(tradingAdapter, sentOrder, request.traceId());
        Instant ackTime = adapterAck.ts() == null ? Instant.now(clock) : adapterAck.ts();

        if (adapterAck.accepted()) {
            orderRepository.updateExternalOrderId(sentOrder.orderId(), adapterAck.externalOrderId(), ackTime);
            OrderRecord acceptedSnapshot = sentOrder.withExternalOrderId(adapterAck.externalOrderId());
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
                            adapterAck.externalOrderId(),
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
                            "venue", acceptedOrder.venue(),
                            "external_order_id", adapterAck.externalOrderId(),
                            "status", acceptedOrder.status().name()
                    )
            );
            return new PlaceOrderResult(acceptedOrder.orderId(), acceptedOrder.status(), false);
        }

        AdapterError error = adapterAck.error();
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
                detail("venue", rejectedOrder.venue(), "reject_code", rejectCode, "reject_reason", rejectReason)
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
        CancelOrderCommand command = new CancelOrderCommand(
                currentOrder.orderId(),
                currentOrder.accountId(),
                currentOrder.venue(),
                currentOrder.symbol(),
                currentOrder.clientOrderId(),
                currentOrder.externalOrderId(),
                request.reason(),
                request.traceId()
        );
        publishEvent(TopicNames.ORDER_COMMAND_V1, currentOrder.clientOrderId(), request.traceId(), command);
        if (currentOrder.status() == OrderStatus.CANCELLED) {
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

        TradingAdapter tradingAdapter = adapterRouter.route(cancelRequestedOrder.accountId(), cancelRequestedOrder.venue()).trading();
        AdapterCancelAck cancelAck = invokeCancelOrder(tradingAdapter, cancelRequestedOrder, request.traceId());
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
        String rejectCode = error == null || error.code() == null || error.code().isBlank()
                ? "CANCEL_REJECTED_BY_ADAPTER"
                : error.code();
        String rejectReason = error == null || error.message() == null || error.message().isBlank()
                ? "adapter rejected cancel"
                : error.message();
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                cancelRequestedOrder.clientOrderId(),
                cancelRequestedOrder.traceId(),
                new CancelReject(
                        cancelRequestedOrder.accountId(),
                        cancelRequestedOrder.venue(),
                        cancelRequestedOrder.clientOrderId(),
                        cancelRequestedOrder.externalOrderId(),
                        rejectCode,
                        rejectReason,
                        ackTime
                )
        );
        auditLogRepository.append(
                "ORDER",
                "ORDER_CANCEL_REJECTED",
                cancelRequestedOrder.orderId(),
                request.traceId(),
                detail(
                        "order_id", cancelRequestedOrder.orderId(),
                        "reject_code", rejectCode,
                        "reject_reason", rejectReason,
                        "venue", cancelRequestedOrder.venue()
                )
        );
        return new CancelOrderResult(cancelRequestedOrder.orderId(), cancelRequestedOrder.status(), false);
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
    public OrderRecord transitionOrder(String orderId, OrderStatus nextStatus, String reason, String traceId) {
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
     * @param orderId 系统订单 ID
     * @param externalOrderId 外部订单号
     * @param traceId 链路追踪 ID
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

    private AdapterOrderAck invokePlaceOrder(TradingAdapter tradingAdapter, OrderRecord order, String traceId) {
        try {
            return tradingAdapter.placeOrder(new AdapterOrderRequest(
                    order.orderId(),
                    order.accountId(),
                    order.venue(),
                    order.symbol(),
                    order.clientOrderId(),
                    order.side(),
                    order.type(),
                    order.price(),
                    order.qty(),
                    order.strategyRunId(),
                    traceId
            ));
        } catch (RuntimeException ex) {
            auditLogRepository.append(
                    "ORDER",
                    "ADAPTER_PLACE_ORDER_FAILED",
                    order.orderId(),
                    traceId,
                    detail("venue", order.venue(), "error", ex.getMessage())
            );
            return new AdapterOrderAck(
                    false,
                    order.venue(),
                    null,
                    new AdapterError("ADAPTER_CALL_FAILED", ex.getMessage(), false),
                    Instant.now(clock),
                    traceId
            );
        }
    }

    private AdapterCancelAck invokeCancelOrder(TradingAdapter tradingAdapter, OrderRecord order, String traceId) {
        try {
            return tradingAdapter.cancelOrder(new AdapterCancelRequest(
                    order.orderId(),
                    order.accountId(),
                    order.venue(),
                    order.symbol(),
                    order.clientOrderId(),
                    order.externalOrderId(),
                    traceId
            ));
        } catch (RuntimeException ex) {
            auditLogRepository.append(
                    "ORDER",
                    "ADAPTER_CANCEL_ORDER_FAILED",
                    order.orderId(),
                    traceId,
                    detail("venue", order.venue(), "error", ex.getMessage())
            );
            return new AdapterCancelAck(
                    false,
                    order.venue(),
                    order.externalOrderId(),
                    new AdapterError("ADAPTER_CALL_FAILED", ex.getMessage(), false),
                    Instant.now(clock),
                    traceId
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
        eventStoreAppender.append(topic, envelope);
    }

    private PlaceOrderCommand toCommand(PlaceOrderRequest request, String orderId) {
        return new PlaceOrderCommand(
                orderId,
                request.accountId(),
                request.venue(),
                request.symbol(),
                request.clientOrderId(),
                request.side().name(),
                request.type().name(),
                request.price(),
                request.qty(),
                "GTC",
                request.strategyRunId(),
                request.traceId()
        );
    }

    private void validateRequest(PlaceOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
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
        if (request.qty() == null || request.qty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("qty must be positive");
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
        if (request.orderId() != null && !request.orderId().isBlank()) {
            return orderRepository.findByOrderId(request.orderId())
                    .orElseThrow(() -> new IllegalArgumentException("order not found: " + request.orderId()));
        }
        return orderRepository.findByAccountAndClientOrderId(request.accountId(), request.clientOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "order not found by accountId/clientOrderId: "
                                + request.accountId() + "/" + request.clientOrderId()
                ));
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
}
