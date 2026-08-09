package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.*;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.trading.application.port.*;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderCommandServiceTest 覆盖 PRE-1 之后的 trading anti-corruption 行为。
 * <p>
 * Why:
 * 本组测试要确保 `OrderCommandService` 只依赖内部 `TradingVenueGateway` 语义，
 * 同时继续保持幂等、状态推进与 accepted/cancel 回执落库行为不变。
 */
class OrderCommandServiceTest {

    /**
     * 验证 placeOrder 会经由内部 gateway，并在成功回执后把 external_order_id 写回订单快照。
     */
    @Test
    void shouldRoutePlaceOrderThroughTradingGatewayAndPersistExternalOrderId() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingVenueGateway tradingVenueGateway = new RecordingTradingVenueGateway();
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingVenueGateway);

        PlaceOrderResult result = service.placeOrder(createRequest("coid-200"));
        Optional<OrderRecord> order = orderRepository.findByOrderId(result.orderId());

        assertEquals(1, tradingVenueGateway.placeInvocationCount());
        assertTrue(order.isPresent());
        assertEquals(OrderStatus.ACCEPTED, result.status());
        assertEquals("paper-ord-ack", order.get().externalOrderId());
        assertEquals("PAPER", order.get().venue());
        assertTrue(auditLogRepository.containsAction("ORDER_ACKED"));
    }

    /**
     * 验证重复 PlaceOrder 命中幂等时返回同一订单且不重复调用 gateway。
     */
    @Test
    void shouldReturnSameOrderIdForIdempotentPlaceOrder() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingVenueGateway tradingVenueGateway = new RecordingTradingVenueGateway();
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingVenueGateway);

        PlaceOrderRequest request = createRequest("coid-201");
        PlaceOrderResult first = service.placeOrder(request);
        PlaceOrderResult second = service.placeOrder(request);

        assertFalse(first.idempotentHit());
        assertTrue(second.idempotentHit());
        assertEquals(first.orderId(), second.orderId());
        assertEquals(1, orderRepository.insertCount());
        assertEquals(1, tradingVenueGateway.placeInvocationCount());
        assertEquals(OrderStatus.ACCEPTED, second.status());
    }

    /**
     * 验证非法迁移会抛错并记录审计日志。
     */
    @Test
    void shouldWriteAuditWhenTransitionIsIllegal() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        OrderCommandService service = createService(orderRepository, auditLogRepository, new RecordingTradingVenueGateway());

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
     * 验证撤单命令会经由内部 gateway 并推进到 CANCELLED 终态。
     */
    @Test
    void shouldCancelAcceptedOrderToCancelledTerminalStatus() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingVenueGateway tradingVenueGateway = new RecordingTradingVenueGateway();
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingVenueGateway);

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
        assertEquals(1, tradingVenueGateway.cancelInvocationCount());
        assertTrue(auditLogRepository.containsAction("ORDER_CANCELLED"));
    }

    /**
     * 验证撤单被 gateway 拒绝时，订单状态会从 CANCEL_REQUESTED 推进到 CANCEL_REJECTED。
     */
    @Test
    void shouldMarkOrderAsCancelRejectedWhenGatewayRejectsCancel() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingVenueGateway tradingVenueGateway = new RecordingTradingVenueGateway();
        tradingVenueGateway.setCancelAccepted(false);
        tradingVenueGateway.setCancelReject("CANCEL_REJECTED_BY_TEST", "reject by test");
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingVenueGateway);

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
        assertEquals(1, tradingVenueGateway.cancelInvocationCount());
        assertTrue(auditLogRepository.containsAction("ORDER_CANCEL_REJECTED"));
    }

    /**
     * 验证 CANCEL_REJECTED 状态可以再次发起撤单并最终推进到 CANCELLED。
     */
    @Test
    void shouldAllowRetryCancelAfterCancelRejected() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingVenueGateway tradingVenueGateway = new RecordingTradingVenueGateway();
        tradingVenueGateway.setCancelAccepted(false);
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingVenueGateway);

        PlaceOrderResult placeOrderResult = service.placeOrder(createRequest("coid-205"));
        CancelOrderResult firstCancel = service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-205-1"
        ));
        assertEquals(OrderStatus.CANCEL_REJECTED, firstCancel.status());

        tradingVenueGateway.setCancelAccepted(true);
        CancelOrderResult secondCancel = service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-205-2"
        ));

        assertEquals(OrderStatus.CANCELLED, secondCancel.status());
        assertEquals(2, tradingVenueGateway.cancelInvocationCount());
    }

    /**
     * 验证外部下单已被 gateway 接受、但本地确认写失败时，不会把订单误标成 ACCEPTED。
     * Why:
     * gateway ack 属于跨边界动作，无法与本地数据库天然原子；当前收口要求至少保证失败时订单停在 `SENT`，
     * 让 query-confirm / recovery 继续接管，而不是留下“看起来已确认”的假成功状态。
     */
    @Test
    void shouldKeepOrderInSentWhenAcceptedAckPersistenceFails() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingVenueGateway tradingVenueGateway = new RecordingTradingVenueGateway();
        OrderCommandWriteService writeService = new FailingFinalizeOrderCommandWriteService(
                orderRepository,
                new InMemoryOrderStateMachine(),
                new AlwaysAllowRiskGate(),
                auditLogRepository,
                new NoopRiskEventRepository(),
                new RecordingEventPublisherPort()
        );
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingVenueGateway, writeService);

        assertThrows(IllegalStateException.class, () -> service.placeOrder(createRequest("coid-206")));

        OrderRecord order = orderRepository.findByAccountAndClientOrderId(1001L, "coid-206").orElseThrow();
        assertEquals(OrderStatus.SENT, order.status());
        assertEquals(null, order.externalOrderId());
    }

    /**
     * 验证 Paper run / order artefact 不会进入正式下单编排。
     * Why:
     * GateM-4 要求 Paper order 不得被提交到 real order path；这里证明 guard 发生在本地写库和 gateway
     * 调用之前，因此不会产生订单事实、不会触达 adapter。
     */
    @Test
    void shouldRejectPaperArtifactBeforePersistingOrCallingGateway() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        RecordingTradingVenueGateway tradingVenueGateway = new RecordingTradingVenueGateway();
        OrderCommandService service = createService(orderRepository, auditLogRepository, tradingVenueGateway);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.placeOrder(new PlaceOrderRequest(
                        "req-paper-order",
                        1001L,
                        "ptr-1",
                        "OKX",
                        "BTC-USDT",
                        "coid-real-should-not-run",
                        "1001:coid-real-should-not-run",
                        "paper_trading",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("0.01000000"),
                        "IOC",
                        "trc-paper-order"
                ))
        );

        assertTrue(ex.getMessage().contains("PAPER_ORDER_NOT_REAL_AUTHORIZATION"));
        assertEquals(0, orderRepository.insertCount());
        assertEquals(0, tradingVenueGateway.placeInvocationCount());
    }

    private OrderCommandService createService(
            InMemoryOrderRepository orderRepository,
            RecordingAuditLogRepository auditLogRepository,
            RecordingTradingVenueGateway tradingVenueGateway
    ) {
        OrderCommandWriteService writeService = new OrderCommandWriteService(
                orderRepository,
                new InMemoryOrderStateMachine(),
                new AlwaysAllowRiskGate(),
                auditLogRepository,
                new NoopRiskEventRepository(),
                new RecordingEventPublisherPort()
        );
        return createService(orderRepository, auditLogRepository, tradingVenueGateway, writeService);
    }

    private OrderCommandService createService(
            InMemoryOrderRepository orderRepository,
            RecordingAuditLogRepository auditLogRepository,
            RecordingTradingVenueGateway tradingVenueGateway,
            OrderCommandWriteService writeService
    ) {
        return new OrderCommandService(
                orderRepository,
                auditLogRepository,
                new RecordingEventPublisherPort(),
                tradingVenueGateway,
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

    /**
     * RecordingTradingVenueGateway 使用内部 gateway 契约驱动 OrderCommandService 单测。
     */
    private static final class RecordingTradingVenueGateway implements TradingVenueGateway {

        private int placeInvocationCount;
        private int cancelInvocationCount;
        private boolean cancelAccepted = true;
        private String cancelRejectCode = "CANCEL_REJECTED_BY_TEST";
        private String cancelRejectReason = "cancel rejected by test adapter";

        @Override
        public TradingPlaceGatewayResult placeOrder(OrderRecord order, PlaceOrderRequest request) {
            placeInvocationCount++;
            return new TradingPlaceGatewayResult(
                    true,
                    "paper-ord-ack",
                    "ACCEPTED",
                    TradingGatewayResultCategory.ACCEPTED,
                    null,
                    Instant.now(),
                    "SIM"
            );
        }

        @Override
        public TradingCancelGatewayResult cancelOrder(OrderRecord order, CancelOrderRequest request) {
            cancelInvocationCount++;
            if (cancelAccepted) {
                return new TradingCancelGatewayResult(
                        true,
                        TradingGatewayResultCategory.ACCEPTED,
                        null,
                        Instant.now(),
                        "SIM"
                );
            }
            return new TradingCancelGatewayResult(
                    false,
                    TradingGatewayResultCategory.FATAL_FAILURE,
                    new TradingGatewayFailure(cancelRejectCode, cancelRejectReason, false),
                    Instant.now(),
                    "SIM"
            );
        }

        @Override
        public TradingOrderStatusSnapshot getOrderStatus(OrderRecord order, String traceId) {
            return new TradingOrderStatusSnapshot(
                    order.externalOrderId(),
                    OrderStatus.ACCEPTED.name(),
                    TradingGatewayResultCategory.SUCCESS,
                    null,
                    Instant.now(),
                    "SIM"
            );
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
                TradingPlaceGatewayResult gatewayResult,
                Instant ackTime
        ) {
            throw new IllegalStateException("simulated accepted-ack persistence failure");
        }
    }
}
