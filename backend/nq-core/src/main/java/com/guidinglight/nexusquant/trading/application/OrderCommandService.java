package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.contracts.command.CancelOrderCommand;
import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.trading.application.boundary.PaperToRealBoundaryGuard;
import com.guidinglight.nexusquant.trading.application.port.TradingCancelGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayResultCategory;
import com.guidinglight.nexusquant.trading.application.port.TradingPlaceGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

/**
 * OrderCommandService 负责 GateD 的统一下单/撤单编排。
 * <p>
 * Why:
 * GateD 需要把 place / cancel 的入口继续保留在一个应用服务内，但也必须避免它重新长成“什么都做”的巨石。
 * 因此本类只负责执行编排、风控调用、venue gateway 调用、event_store 与审计写入；
 * 生命周期语义动作统一收口到 `OrderLifecycleService`，contracts 组装统一收口到 `ExecutionCommandMapper`。
 */
@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);
    private static final String SOURCE = "nq-core.order-command-service";

    private final OrderRepository orderRepository;
    private final AuditLogRepository auditLogRepository;
    private final EventPublisherPort eventPublisherPort;
    private final TradingVenueGateway tradingVenueGateway;
    private final OrderCommandWriteService orderCommandWriteService;
    private final Clock clock;

    /**
     * @param orderRepository          订单仓储端口
     * @param auditLogRepository       审计仓储
     * @param eventPublisherPort       事件事实链追加端口
     * @param tradingVenueGateway      trading anti-corruption boundary
     * @param orderCommandWriteService 本地写阶段事务服务
     */
    public OrderCommandService(
            OrderRepository orderRepository,
            AuditLogRepository auditLogRepository,
            EventPublisherPort eventPublisherPort,
            TradingVenueGateway tradingVenueGateway,
            OrderCommandWriteService orderCommandWriteService
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.tradingVenueGateway = Objects.requireNonNull(
                tradingVenueGateway,
                "tradingVenueGateway must not be null"
        );
        this.orderCommandWriteService = Objects.requireNonNull(
                orderCommandWriteService,
                "orderCommandWriteService must not be null"
        );
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

        OrderCommandWriteService.PlaceOrderPreparation preparation = orderCommandWriteService.preparePlaceOrder(
                request,
                command,
                candidateOrderId,
                now
        );
        if (preparation.completedResult() != null) {
            return preparation.completedResult();
        }
        OrderRecord sentOrder = preparation.sentOrder();
        TradingPlaceGatewayResult gatewayResult = tradingVenueGateway.placeOrder(sentOrder, request);
        Instant ackTime = gatewayResult.acknowledgedAt() == null ? Instant.now(clock) : gatewayResult.acknowledgedAt();

        if (gatewayResult.accepted()) {
            return orderCommandWriteService.finalizeAcceptedPlaceOrder(request, sentOrder, gatewayResult, ackTime);
        }

        if (shouldDeferOrderRejection(gatewayResult.resultCategory())) {
            return orderCommandWriteService.finalizeDeferredPlaceOrder(request, sentOrder, gatewayResult);
        }
        return orderCommandWriteService.finalizeRejectedPlaceOrder(request, sentOrder, gatewayResult, ackTime);
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

        OrderRecord cancelRequestedOrder = orderCommandWriteService.prepareCancelOrder(request, currentOrder);

        logCancelPath("order_cancel_before_adapter_call", cancelRequestedOrder, request.traceId());
        TradingCancelGatewayResult gatewayResult = tradingVenueGateway.cancelOrder(cancelRequestedOrder, request);
        log.info(
                "order_cancel_after_adapter_call orderId={} clientOrderId={} externalOrderId={} accountId={} currentStatus={} traceId={} venue={} adapterAccepted={}",
                cancelRequestedOrder.orderId(),
                cancelRequestedOrder.clientOrderId(),
                cancelRequestedOrder.externalOrderId(),
                cancelRequestedOrder.accountId(),
                cancelRequestedOrder.status().name(),
                request.traceId(),
                cancelRequestedOrder.venue(),
                gatewayResult.accepted()
        );
        Instant ackTime = gatewayResult.acknowledgedAt() == null ? Instant.now(clock) : gatewayResult.acknowledgedAt();
        if (gatewayResult.accepted()) {
            return orderCommandWriteService.finalizeAcceptedCancelOrder(request, cancelRequestedOrder, ackTime);
        }

        if (shouldDeferOrderRejection(gatewayResult.resultCategory())) {
            return orderCommandWriteService.finalizeDeferredCancelOrder(request, cancelRequestedOrder, gatewayResult);
        }
        return orderCommandWriteService.finalizeRejectedCancelOrder(request, cancelRequestedOrder, gatewayResult, ackTime);
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
        return orderCommandWriteService.transitionOrder(orderId, nextStatus, reason, traceId);
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
        return orderCommandWriteService.linkExternalOrderId(orderId, externalOrderId, traceId);
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
        PaperToRealBoundaryGuard.requireNoPaperArtifactForRealOrderPath(request);
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
        PaperToRealBoundaryGuard.requireNoPaperArtifactForRealCancelPath(request);
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

    private boolean shouldDeferOrderRejection(TradingGatewayResultCategory resultCategory) {
        if (resultCategory == null) {
            return false;
        }
        return resultCategory.shouldDeferDecision();
    }
}


