package com.guidinglight.nexusquant.trading.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.service.NoopAccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopMarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.trading.application.routing.AdapterRouter;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;
import com.guidinglight.nexusquant.risk.service.RiskGate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * OrderCommandServiceTest 覆盖 GateC-0 的 adapter 路由、幂等与外部单号落库行为。
 */
class OrderCommandServiceTest {

    /**
     * 验证 placeOrder 会调用 TradingAdapter，并在成功回执后把 external_order_id 写回订单快照。
     */
    @Test
    void shouldRoutePlaceOrderThroughTradingAdapterAndPersistExternalOrderId() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingAdapter tradingAdapter = new RecordingTradingAdapter();
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingAdapter);

        PlaceOrderResult result = service.placeOrder(createRequest("coid-200"));
        Optional<OrderRecord> order = orderRepository.findByOrderId(result.orderId());

        assertEquals(1, tradingAdapter.placeInvocationCount());
        assertTrue(order.isPresent());
        assertEquals(OrderStatus.ACCEPTED, result.status());
        assertEquals("paper-ord-ack", order.get().externalOrderId());
        assertEquals("PAPER", order.get().venue());
        assertTrue(auditLogRepository.containsAction("ORDER_ACKED"));
    }

    /**
     * 验证重复 PlaceOrder 命中幂等时返回同一订单且不重复调用 adapter。
     */
    @Test
    void shouldReturnSameOrderIdForIdempotentPlaceOrder() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingAdapter tradingAdapter = new RecordingTradingAdapter();
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingAdapter);

        PlaceOrderRequest request = createRequest("coid-201");
        PlaceOrderResult first = service.placeOrder(request);
        PlaceOrderResult second = service.placeOrder(request);

        assertFalse(first.idempotentHit());
        assertTrue(second.idempotentHit());
        assertEquals(first.orderId(), second.orderId());
        assertEquals(1, orderRepository.insertCount());
        assertEquals(1, tradingAdapter.placeInvocationCount());
        assertEquals(OrderStatus.ACCEPTED, second.status());
    }

    /**
     * 验证非法迁移会抛错并记录审计日志。
     */
    @Test
    void shouldWriteAuditWhenTransitionIsIllegal() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        OrderCommandService service = createService(orderRepository, auditLogRepository, new RecordingTradingAdapter());

        PlaceOrderResult result = service.placeOrder(createRequest("coid-202"));

        assertThrows(
                IllegalStateException.class,
                () -> service.transitionOrder(result.orderId(), OrderStatus.NEW, "ILLEGAL_BACK_TRANSITION", "trc-test-202")
        );
        Optional<OrderRecord> order = orderRepository.findByOrderId(result.orderId());
        assertTrue(order.isPresent());
        assertEquals(OrderStatus.ACCEPTED, order.get().status());
        assertTrue(auditLogRepository.containsAction("ORDER_STATUS_TRANSITION_REJECTED"));
    }

    /**
     * 验证撤单命令会经由 TradingAdapter 并推进到 CANCELLED 终态。
     */
    @Test
    void shouldCancelAcceptedOrderToCancelledTerminalStatus() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingAdapter tradingAdapter = new RecordingTradingAdapter();
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingAdapter);

        PlaceOrderResult placeOrderResult = service.placeOrder(createRequest("coid-203"));
        CancelOrderResult cancelOrderResult = service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-203"
        ));

        assertEquals(OrderStatus.CANCELLED, cancelOrderResult.status());
        assertFalse(cancelOrderResult.idempotentHit());
        assertEquals(1, tradingAdapter.cancelInvocationCount());
        assertTrue(auditLogRepository.containsAction("ORDER_CANCELLED"));
    }

    /**
     * 验证撤单被 adapter 拒绝时，订单状态会从 CANCEL_REQUESTED 推进到 CANCEL_REJECTED。
     */
    @Test
    void shouldMarkOrderAsCancelRejectedWhenAdapterRejectsCancel() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingAdapter tradingAdapter = new RecordingTradingAdapter();
        tradingAdapter.setCancelAccepted(false);
        tradingAdapter.setCancelReject("CANCEL_REJECTED_BY_TEST", "reject by test");
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingAdapter);

        PlaceOrderResult placeOrderResult = service.placeOrder(createRequest("coid-204"));
        CancelOrderResult cancelOrderResult = service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-204"
        ));

        assertEquals(OrderStatus.CANCEL_REJECTED, cancelOrderResult.status());
        assertFalse(cancelOrderResult.idempotentHit());
        assertEquals(1, tradingAdapter.cancelInvocationCount());
        assertTrue(auditLogRepository.containsAction("ORDER_CANCEL_REJECTED"));
    }

    /**
     * 验证 CANCEL_REJECTED 状态可以再次发起撤单并最终推进到 CANCELLED。
     */
    @Test
    void shouldAllowRetryCancelAfterCancelRejected() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingAdapter tradingAdapter = new RecordingTradingAdapter();
        tradingAdapter.setCancelAccepted(false);
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingAdapter);

        PlaceOrderResult placeOrderResult = service.placeOrder(createRequest("coid-205"));
        CancelOrderResult firstCancel = service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-205-1"
        ));
        assertEquals(OrderStatus.CANCEL_REJECTED, firstCancel.status());

        tradingAdapter.setCancelAccepted(true);
        CancelOrderResult secondCancel = service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-205-2"
        ));

        assertEquals(OrderStatus.CANCELLED, secondCancel.status());
        assertEquals(2, tradingAdapter.cancelInvocationCount());
    }

    /**
     * 验证外部下单已被 adapter 接受、但本地确认写失败时，不会把订单误标成 ACCEPTED。
     * Why:
     * adapter ack 属于跨边界动作，无法与本地数据库天然原子；当前收口要求至少保证失败时订单停在 `SENT`，
     * 让 query-confirm / recovery 继续接管，而不是留下“看起来已确认”的假成功状态。
     */
    @Test
    void shouldKeepOrderInSentWhenAcceptedAckPersistenceFails() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingAdapter tradingAdapter = new RecordingTradingAdapter();
        OrderCommandWriteService writeService = new FailingFinalizeOrderCommandWriteService(
                orderRepository,
                new InMemoryOrderStateMachine(),
                new AlwaysAllowRiskGate(),
                auditLogRepository,
                new NoopRiskEventRepository(),
                new RecordingEventPublisherPort()
        );
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingAdapter, writeService);

        assertThrows(IllegalStateException.class, () -> service.placeOrder(createRequest("coid-206")));

        OrderRecord order = orderRepository.findByAccountAndClientOrderId(1001L, "coid-206").orElseThrow();
        assertEquals(OrderStatus.SENT, order.status());
        assertEquals(null, order.externalOrderId());
    }

    private OrderCommandService createService(
            InMemoryOrderRepository orderRepository,
            RecordingAuditLogRepository auditLogRepository,
            RecordingTradingAdapter tradingAdapter
    ) {
        OrderCommandWriteService writeService = new OrderCommandWriteService(
                orderRepository,
                new InMemoryOrderStateMachine(),
                new AlwaysAllowRiskGate(),
                auditLogRepository,
                new NoopRiskEventRepository(),
                new RecordingEventPublisherPort()
        );
        return createService(orderRepository, auditLogRepository, tradingAdapter, writeService);
    }

    private OrderCommandService createService(
            InMemoryOrderRepository orderRepository,
            RecordingAuditLogRepository auditLogRepository,
            RecordingTradingAdapter tradingAdapter,
            OrderCommandWriteService writeService
    ) {
        AdapterRouter adapterRouter = new AdapterRouter(
                List.of(tradingAdapter),
                List.of(new NoopMarketDataAdapter("PAPER")),
                List.of(new NoopAccountAdapter("PAPER"))
        );
        return new OrderCommandService(
                orderRepository,
                auditLogRepository,
                new RecordingEventPublisherPort(),
                adapterRouter,
                writeService
        );
    }

    private PlaceOrderRequest createRequest(String clientOrderId) {
        return new PlaceOrderRequest(
                1001L,
                "run-1001",
                "PAPER",
                clientOrderId,
                "BTC-USDT",
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                new BigDecimal("0.01000000"),
                "trc-test-" + clientOrderId
        );
    }

    /**
     * InMemoryOrderRepository 用于单测幂等、状态更新与 external_order_id 落点，不依赖外部数据库。
     */
    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<String, OrderRecord> byOrderId = new HashMap<>();
        private final Map<String, String> orderIdByIdempotencyKey = new HashMap<>();
        private int insertCount;

        @Override
        public Optional<OrderRecord> findByAccountAndClientOrderId(Long accountId, String clientOrderId) {
            String orderId = orderIdByIdempotencyKey.get(buildKey(accountId, clientOrderId));
            if (orderId == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(byOrderId.get(orderId));
        }

        @Override
        public Optional<OrderRecord> findByOrderId(String orderId) {
            return Optional.ofNullable(byOrderId.get(orderId));
        }

        @Override
        public void insert(OrderRecord order, Instant now) {
            byOrderId.put(order.orderId(), order);
            orderIdByIdempotencyKey.put(buildKey(order.accountId(), order.clientOrderId()), order.orderId());
            insertCount++;
        }

        @Override
        public void updateStatus(String orderId, OrderStatus status, String reason, Instant now) {
            OrderRecord existing = byOrderId.get(orderId);
            byOrderId.put(orderId, existing.withStatus(status, reason));
        }

        @Override
        public void updateExternalOrderId(String orderId, String externalOrderId, Instant now) {
            OrderRecord existing = byOrderId.get(orderId);
            byOrderId.put(orderId, existing.withExternalOrderId(externalOrderId));
        }

        @Override
        public List<OrderRecord> findByStatuses(Collection<OrderStatus> statuses, int limit) {
            return byOrderId.values().stream()
                    .filter(order -> statuses.contains(order.status()))
                    .sorted(Comparator.comparing(OrderRecord::orderId))
                    .limit(limit)
                    .toList();
        }

        int insertCount() {
            return insertCount;
        }

        private String buildKey(Long accountId, String clientOrderId) {
            return accountId + ":" + clientOrderId;
        }
    }

    private static final class RecordingTradingAdapter implements TradingAdapter {

        private int placeInvocationCount;
        private int cancelInvocationCount;
        private boolean cancelAccepted = true;
        private String cancelRejectCode = "CANCEL_REJECTED_BY_TEST";
        private String cancelRejectReason = "cancel rejected by test adapter";

        @Override
        public String venue() {
            return "PAPER";
        }

        @Override
        public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
            placeInvocationCount++;
            return new AdapterOrderAck(
                    true,
                    venue(),
                    request.accountId(),
                    request.symbol(),
                    request.clientOrderId(),
                    "paper-ord-ack",
                    "ACCEPTED",
                    null,
                    Instant.now(),
                    "paper_test_ack",
                    request.traceId()
            );
        }

        @Override
        public AdapterCancelAck cancelOrder(AdapterCancelRequest request) {
            cancelInvocationCount++;
            if (cancelAccepted) {
                return new AdapterCancelAck(true, venue(), request.externalOrderId(), null, Instant.now(), request.traceId());
            }
            return new AdapterCancelAck(
                    false,
                    venue(),
                    request.externalOrderId(),
                    new com.guidinglight.nexusquant.adapter.api.model.AdapterError(
                            cancelRejectCode,
                            cancelRejectReason,
                            false
                    ),
                    Instant.now(),
                    request.traceId()
            );
        }

        @Override
        public AdapterOrderSnapshot getOrder(AdapterOrderQuery query) {
            return new AdapterOrderSnapshot(
                    query.accountId(),
                    venue(),
                    query.symbol(),
                    query.clientOrderId(),
                    query.externalOrderId(),
                    OrderStatus.ACCEPTED.name(),
                    null,
                    null,
                    null,
                    null,
                    Instant.now(),
                    "paper_test_snapshot",
                    query.traceId()
            );
        }

        @Override
        public List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query) {
            return List.of();
        }

        int placeInvocationCount() {
            return placeInvocationCount;
        }

        int cancelInvocationCount() {
            return cancelInvocationCount;
        }

        void setCancelAccepted(boolean cancelAccepted) {
            this.cancelAccepted = cancelAccepted;
        }

        void setCancelReject(String code, String reason) {
            this.cancelRejectCode = code;
            this.cancelRejectReason = reason;
        }
    }

    private static final class RecordingEventPublisherPort implements EventPublisherPort {
        @Override
        public void append(String topic, EventEnvelope<?> envelope) {
            // Why: Step 3 单测只验证业务编排，不依赖 infra 的 JDBC event_store 实现。
        }
    }

    private static final class AlwaysAllowRiskGate implements RiskGate {
        @Override
        public RiskDecisionResult evaluate(RiskContext context) {
            return RiskDecisionResult.allow("ALLOW_IN_TEST", "AlwaysAllowRiskGate", context.traceId());
        }
    }

    private static final class NoopRiskEventRepository implements RiskEventRepository {
        @Override
        public void append(
                String scope,
                String scopeId,
                RiskDecision decision,
                String reason,
                RiskSeverity severity,
                String traceId
        ) {
            // 单测不校验 risk_events 落库，忽略写入。
        }
    }

    private static final class RecordingAuditLogRepository implements AuditLogRepository {

        private final List<String> actions = new ArrayList<>();

        @Override
        public void append(String domain, String action, String actorId, String traceId, Map<String, Object> detail) {
            actions.add(action);
        }

        boolean containsAction(String action) {
            return actions.contains(action);
        }
    }

    private static final class FailingFinalizeOrderCommandWriteService extends OrderCommandWriteService {

        private FailingFinalizeOrderCommandWriteService(
                OrderRepository orderRepository,
                InMemoryOrderStateMachine orderStateMachine,
                RiskGate riskGate,
                AuditLogRepository auditLogRepository,
                RiskEventRepository riskEventRepository,
                EventPublisherPort eventPublisherPort
        ) {
            super(
                    orderRepository,
                    orderStateMachine,
                    riskGate,
                    auditLogRepository,
                    riskEventRepository,
                    eventPublisherPort
            );
        }

        @Override
        public PlaceOrderResult finalizeAcceptedPlaceOrder(
                PlaceOrderRequest request,
                OrderRecord sentOrder,
                AdapterOrderAck adapterAck,
                Instant ackTime
        ) {
            throw new IllegalStateException("simulated accepted-ack persistence failure");
        }
    }
}

