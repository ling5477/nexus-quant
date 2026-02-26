package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PaperMatchingServiceTest 验证 paper 撮合幂等。
 */
class PaperMatchingServiceTest {

    /**
     * 重复执行撮合 tick 时，同一订单只会生成一笔 trade。
     */
    @Test
    void shouldNotCreateDuplicateTradeOnRepeatedTick() {
        InMemoryOrderExecutionGateway orderGateway = new InMemoryOrderExecutionGateway();
        InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
        RecordingJdbcTemplate eventStoreJdbcTemplate = new RecordingJdbcTemplate();
        EventStoreAppender eventStoreAppender = new EventStoreAppender(
                eventStoreJdbcTemplate,
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        );
        PaperMatchingService matchingService = new PaperMatchingService(
                orderGateway,
                tradeRepository,
                new AlwaysPostedLedgerGateway(),
                eventStoreAppender,
                new NoopAuditLogRepository()
        );

        orderGateway.addOrder(new OrderRecord(
                "ord-300",
                1001L,
                "run-300",
                "BTC-USDT",
                "coid-300",
                "BUY",
                "MARKET",
                null,
                new BigDecimal("0.01000000"),
                OrderStatus.SENT,
                "SUBMITTED_TO_PAPER",
                "trc-300"
        ));

        int firstTickTrades = matchingService.matchOnce(10);
        int secondTickTrades = matchingService.matchOnce(10);

        assertEquals(1, firstTickTrades);
        assertEquals(0, secondTickTrades);
        assertEquals(1, tradeRepository.size());
        assertEquals(1, orderGateway.transitionCount());
        assertEquals(1, eventStoreJdbcTemplate.updateCount());
    }

    /**
     * 验证 LIMIT BUY 在市场价高于限价时不会成交，订单保持待撮合状态。
     */
    @Test
    void shouldKeepLimitBuyOrderPendingWhenPriceNotReached() {
        InMemoryOrderExecutionGateway orderGateway = new InMemoryOrderExecutionGateway();
        InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
        PaperMatchingService matchingService = new PaperMatchingService(
                orderGateway,
                tradeRepository,
                new AlwaysPostedLedgerGateway(),
                createEventStoreAppender(),
                new NoopAuditLogRepository()
        );
        orderGateway.addOrder(new OrderRecord(
                "ord-301",
                1001L,
                "run-301",
                "BTC-USDT",
                "coid-301",
                "BUY",
                OrderType.LIMIT.name(),
                new BigDecimal("90.00000000"),
                new BigDecimal("0.01000000"),
                OrderStatus.SENT,
                "SUBMITTED_TO_PAPER",
                "trc-301"
        ));

        int matched = matchingService.matchOnce(10);

        assertEquals(0, matched);
        assertEquals(0, tradeRepository.size());
        assertEquals(0, orderGateway.transitionCount());
    }

    /**
     * 验证 LIMIT BUY 在市场价低于等于限价时成交，并推进订单到 FILLED。
     */
    @Test
    void shouldFillLimitBuyOrderWhenPriceReached() {
        InMemoryOrderExecutionGateway orderGateway = new InMemoryOrderExecutionGateway();
        InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
        PaperMatchingService matchingService = new PaperMatchingService(
                orderGateway,
                tradeRepository,
                new AlwaysPostedLedgerGateway(),
                createEventStoreAppender(),
                new NoopAuditLogRepository()
        );
        orderGateway.addOrder(new OrderRecord(
                "ord-302",
                1001L,
                "run-302",
                "BTC-USDT",
                "coid-302",
                "BUY",
                OrderType.LIMIT.name(),
                new BigDecimal("120.00000000"),
                new BigDecimal("0.01000000"),
                OrderStatus.SENT,
                "SUBMITTED_TO_PAPER",
                "trc-302"
        ));

        int matched = matchingService.matchOnce(10);

        assertEquals(1, matched);
        assertEquals(1, tradeRepository.size());
        assertEquals(1, orderGateway.transitionCount());
    }

    private EventStoreAppender createEventStoreAppender() {
        return new EventStoreAppender(
                new RecordingJdbcTemplate(),
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        );
    }

    private static final class InMemoryOrderExecutionGateway implements OrderExecutionGateway {

        private final Map<String, OrderRecord> orders = new HashMap<>();
        private int transitionCount;

        @Override
        public List<OrderRecord> findMatchableOrders(int limit) {
            return orders.values().stream()
                    .filter(order -> order.status() == OrderStatus.SENT || order.status() == OrderStatus.ACCEPTED)
                    .limit(limit)
                    .toList();
        }

        @Override
        public OrderRecord transition(String orderId, OrderStatus nextStatus, String reason, String traceId) {
            OrderRecord existing = orders.get(orderId);
            OrderRecord transitioned = existing.withStatus(nextStatus, reason);
            orders.put(orderId, transitioned);
            transitionCount++;
            return transitioned;
        }

        void addOrder(OrderRecord order) {
            orders.put(order.orderId(), order);
        }

        int transitionCount() {
            return transitionCount;
        }
    }

    private static final class InMemoryTradeRepository implements TradeRepository {

        private final Map<String, PaperTradeRecord> tradesByOrderId = new HashMap<>();

        @Override
        public Optional<PaperTradeRecord> findByOrderId(String orderId) {
            return Optional.ofNullable(tradesByOrderId.get(orderId));
        }

        @Override
        public void insert(PaperTradeRecord trade) {
            tradesByOrderId.put(trade.orderId(), trade);
        }

        int size() {
            return tradesByOrderId.size();
        }
    }

    private static final class NoopAuditLogRepository implements AuditLogRepository {
        @Override
        public void append(String domain, String action, String actorId, String traceId, Map<String, Object> detail) {
            // 本测试仅验证撮合幂等，不校验审计副作用。
        }
    }

    private static final class AlwaysPostedLedgerGateway implements TradeLedgerGateway {
        @Override
        public com.guidinglight.nexusquant.ledger.model.LedgerPostingResult postTrade(
                com.guidinglight.nexusquant.ledger.model.TradeLedgerRequest request
        ) {
            return new com.guidinglight.nexusquant.ledger.model.LedgerPostingResult(true, false, "POSTED");
        }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private int updateCount;

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            return 1;
        }

        int updateCount() {
            return updateCount;
        }
    }
}
