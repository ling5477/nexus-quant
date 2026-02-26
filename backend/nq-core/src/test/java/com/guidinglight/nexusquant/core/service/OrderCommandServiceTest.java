package com.guidinglight.nexusquant.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.core.service.port.OrderRepository;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.core.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * OrderCommandServiceTest 覆盖下单幂等与非法迁移审计行为。
 */
class OrderCommandServiceTest {

    /**
     * 验证重复 PlaceOrder 命中幂等时返回同一订单且不重复插入。
     */
    @Test
    void shouldReturnSameOrderIdForIdempotentPlaceOrder() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        OrderCommandService service = createService(orderRepository, auditLogRepository);

        PlaceOrderRequest request = createRequest("coid-200");
        PlaceOrderResult first = service.placeOrder(request);
        PlaceOrderResult second = service.placeOrder(request);

        assertFalse(first.idempotentHit());
        assertTrue(second.idempotentHit());
        assertEquals(first.orderId(), second.orderId());
        assertEquals(1, orderRepository.insertCount());
        assertEquals(OrderStatus.SENT, second.status());
    }

    /**
     * 验证非法迁移会抛错并记录审计日志。
     */
    @Test
    void shouldWriteAuditWhenTransitionIsIllegal() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        OrderCommandService service = createService(orderRepository, auditLogRepository);

        PlaceOrderResult result = service.placeOrder(createRequest("coid-201"));

        assertThrows(
                IllegalStateException.class,
                () -> service.transitionOrder(result.orderId(), OrderStatus.NEW, "ILLEGAL_BACK_TRANSITION", "trc-test-201")
        );
        Optional<OrderRecord> order = orderRepository.findByOrderId(result.orderId());
        assertTrue(order.isPresent());
        assertEquals(OrderStatus.SENT, order.get().status());
        assertTrue(auditLogRepository.containsAction("ORDER_STATUS_TRANSITION_REJECTED"));
    }

    /**
     * 验证撤单命令会通过状态机推进到 CANCELLED 终态。
     */
    @Test
    void shouldCancelSentOrderToCancelledTerminalStatus() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        OrderCommandService service = createService(orderRepository, auditLogRepository);

        PlaceOrderResult placeOrderResult = service.placeOrder(createRequest("coid-202"));
        CancelOrderResult cancelOrderResult = service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-202"
        ));

        assertEquals(OrderStatus.CANCELLED, cancelOrderResult.status());
        assertFalse(cancelOrderResult.idempotentHit());
        assertTrue(auditLogRepository.containsAction("ORDER_CANCELLED"));
    }

    /**
     * 验证终态订单再次撤单会被幂等短路，不产生新的状态副作用。
     */
    @Test
    void shouldReturnIdempotentHitWhenCancelAlreadyCancelledOrder() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        OrderCommandService service = createService(orderRepository, auditLogRepository);

        PlaceOrderResult placeOrderResult = service.placeOrder(createRequest("coid-203"));
        service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-203-first"
        ));
        CancelOrderResult secondCancelResult = service.cancelOrder(new CancelOrderRequest(
                placeOrderResult.orderId(),
                null,
                null,
                "USER_REQUESTED",
                "trc-cancel-203-second"
        ));

        assertEquals(OrderStatus.CANCELLED, secondCancelResult.status());
        assertTrue(secondCancelResult.idempotentHit());
        assertTrue(auditLogRepository.containsAction("CANCEL_ORDER_IDEMPOTENT_HIT"));
    }

    private OrderCommandService createService(
            InMemoryOrderRepository orderRepository,
            RecordingAuditLogRepository auditLogRepository
    ) {
        EventStoreAppender eventStoreAppender = new EventStoreAppender(
                new RecordingJdbcTemplate(),
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        );
        return new OrderCommandService(
                orderRepository,
                new InMemoryOrderStateMachine(),
                new AlwaysAllowRiskGate(),
                auditLogRepository,
                new NoopRiskEventRepository(),
                eventStoreAppender
        );
    }

    private PlaceOrderRequest createRequest(String clientOrderId) {
        return new PlaceOrderRequest(
                1001L,
                "run-1001",
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
     * InMemoryOrderRepository 用于单测幂等与状态更新，不依赖外部数据库。
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

    private static final class AlwaysAllowRiskGate implements RiskGate {
        @Override
        public RiskDecisionResult evaluate(RiskContext context) {
            return new RiskDecisionResult(RiskDecision.ALLOW, "ALLOW_IN_TEST", RiskSeverity.LOW, context.traceId());
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

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        @Override
        public int update(String sql, Object... args) {
            return 1;
        }
    }
}
