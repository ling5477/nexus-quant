package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.OrderCreated;
import com.guidinglight.nexusquant.contracts.event.OrderStatusChangedPayload;
import com.guidinglight.nexusquant.contracts.event.OrderSubmitted;
import com.guidinglight.nexusquant.contracts.event.RiskPassed;
import com.guidinglight.nexusquant.contracts.event.RiskRejected;
import com.guidinglight.nexusquant.contracts.event.RiskEventRaised;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.command.CancelOrderCommand;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
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
 * OrderCommandService 负责 Gate B 下单编排主链路。
 * <p>
 * Why:
 * 下单链路需要同时满足幂等、状态机、审计和事件回放四类约束，
 * 若逻辑分散在多个入口会导致状态与副作用失配，因此集中在一个服务内统一执行业务规则。
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
    private final Clock clock;

    /**
     * @param orderRepository     订单仓储端口
     * @param orderStateMachine   订单状态机
     * @param riskGate            风控服务
     * @param auditLogRepository  审计仓储
     * @param riskEventRepository 风控事件仓储
     * @param eventStoreAppender  event_store 写入器
     */
    public OrderCommandService(
            OrderRepository orderRepository,
            OrderStateMachine orderStateMachine,
            RiskGate riskGate,
            AuditLogRepository auditLogRepository,
            RiskEventRepository riskEventRepository,
            EventStoreAppender eventStoreAppender
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.orderStateMachine = Objects.requireNonNull(orderStateMachine, "orderStateMachine must not be null");
        this.riskGate = Objects.requireNonNull(riskGate, "riskGate must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.riskEventRepository = Objects.requireNonNull(riskEventRepository, "riskEventRepository must not be null");
        this.eventStoreAppender = Objects.requireNonNull(eventStoreAppender, "eventStoreAppender must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 执行下单编排。
     * <p>
     * Why:
     * 该方法是“命令 -> 风控 -> 状态迁移 -> 事件记录”的唯一入口，保证重复请求不会生成重复订单。
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
                    detail("account_id", order.accountId(), "client_order_id", order.clientOrderId(), "status", order.status().name())
            );
            return new PlaceOrderResult(order.orderId(), order.status(), true);
        }

        OrderRecord createdOrder = new OrderRecord(
                candidateOrderId,
                request.accountId(),
                request.strategyRunId(),
                request.symbol(),
                request.clientOrderId(),
                request.side().name(),
                request.type().name(),
                request.price(),
                request.qty(),
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
                detail("order_id", createdOrder.orderId(), "client_order_id", createdOrder.clientOrderId())
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
                    detail("reason", riskDecision.reasonCode())
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
                "SUBMITTED_TO_PAPER",
                request.traceId()
        );
        publishEvent(
                TopicNames.ORDER_EVENT_V1,
                sentOrder.clientOrderId(),
                sentOrder.traceId(),
                new OrderSubmitted(
                        sentOrder.orderId(),
                        sentOrder.clientOrderId(),
                        "PAPER",
                        sentOrder.status().name(),
                        sentOrder.reason(),
                        now
                )
        );
        auditLogRepository.append(
                "ORDER",
                "ORDER_SUBMITTED",
                sentOrder.orderId(),
                request.traceId(),
                detail("status", sentOrder.status().name(), "reason", sentOrder.reason())
        );
        return new PlaceOrderResult(sentOrder.orderId(), sentOrder.status(), false);
    }

    /**
     * 执行撤单编排。
     * <p>
     * Why:
     * Gate B 的撤单必须复用状态机与审计口径，不能由调用方直接写 orders.status，
     * 否则会破坏恢复与回放的一致性。
     *
     * @param request 撤单请求
     * @return 撤单结果，包含订单状态与是否命中幂等
     */
    public CancelOrderResult cancelOrder(CancelOrderRequest request) {
        validateCancelRequest(request);
        OrderRecord currentOrder = resolveCancelTarget(request);
        String reason = request.reason();
        CancelOrderCommand command = new CancelOrderCommand(
                currentOrder.orderId(),
                currentOrder.accountId(),
                currentOrder.clientOrderId(),
                reason,
                request.traceId()
        );
        publishEvent(TopicNames.ORDER_COMMAND_V1, currentOrder.clientOrderId(), request.traceId(), command);
        if (currentOrder.status() == OrderStatus.CANCELLED) {
            auditLogRepository.append(
                    "ORDER",
                    "CANCEL_ORDER_IDEMPOTENT_HIT",
                    currentOrder.orderId(),
                    request.traceId(),
                    detail("order_id", currentOrder.orderId(), "status", currentOrder.status().name(), "reason", reason)
            );
            return new CancelOrderResult(currentOrder.orderId(), currentOrder.status(), true);
        }
        OrderRecord cancelRequestedOrder = transitionOrder(
                currentOrder,
                OrderStatus.CANCEL_REQUESTED,
                reason,
                request.traceId()
        );
        publishOrderStatusChanged(cancelRequestedOrder, request.traceId(), "ORDER_CANCEL_REQUESTED");
        OrderRecord cancelledOrder = transitionOrder(
                cancelRequestedOrder,
                OrderStatus.CANCELLED,
                reason,
                request.traceId()
        );
        publishOrderStatusChanged(cancelledOrder, request.traceId(), "ORDER_CANCELLED");
        auditLogRepository.append(
                "ORDER",
                "ORDER_CANCELLED",
                cancelledOrder.orderId(),
                request.traceId(),
                detail("order_id", cancelledOrder.orderId(), "status", cancelledOrder.status().name(), "reason", reason)
        );
        return new CancelOrderResult(cancelledOrder.orderId(), cancelledOrder.status(), false);
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
                            "from",
                            currentOrder.status().name(),
                            "to",
                            nextStatus.name(),
                            "reason",
                            reason,
                            "error",
                            ex.getMessage()
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
        eventStoreAppender.append(topic, envelope);
    }

    private PlaceOrderCommand toCommand(PlaceOrderRequest request, String orderId) {
        return new PlaceOrderCommand(
                orderId,
                request.accountId(),
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
