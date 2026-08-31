package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxInstrumentsCache;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.ledger.service.TradeLedgerPostingService;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.PreTradeRiskService;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.scheduler.service.LedgerReconcileScheduler;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.port.TradingCancelGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayResultCategory;
import com.guidinglight.nexusquant.trading.application.port.TradingOrderStatusSnapshot;
import com.guidinglight.nexusquant.trading.application.port.TradingPlaceGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.trading.domain.state.OrderStateMachine;

import java.math.BigDecimal;
import java.net.ProxySelector;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 4A L3 causal proof using the production Spring graph and a real PostgreSQL database.
 *
 * <p>The test-only deterministic venue accepts PLACE through {@link TradingVenueGateway}, then
 * exposes order and fill facts through a scripted no-network {@link OkxHttpClient}. The production
 * {@link OkxExchangeAdapter} maps those facts and {@link OkxRestReconcileService} is the only Trade
 * producer in these scenarios. Risk, order state, JDBC repositories, ledger, position projection,
 * reconciliation, audit and event persistence remain the production implementations.</p>
 */
@SpringBootTest(
        classes = {NexusQuantApplication.class, TradingChainPostgresIntegrationTest.FakeVenueConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("local")
@ContextConfiguration(initializers = TradingChainPostgresIntegrationTest.NoExchangeOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "nq.runtime.trading-components.enabled=true",
        "nq.validation-operations.scheduler.enabled=false",
        "nq.okx.recovery.enabled=false",
        "nq.okx.ws.enabled=false",
        "nq.binance.ws.enabled=false",
        "nq.instrument.catalog-sync.enabled=false",
        "nq.env-safety.live-enabled=false",
        "nq.env-safety.ai-enabled=false",
        "nq.env-safety.dh-runtime-enabled=false",
        "nq.env-safety.real-provider-enabled=false",
        "nq.env-safety.real-client-enabled=false",
        "nq.env-safety.real-exchange-enabled=false",
        "nq.env-safety.no-outbound=true"
})
@Transactional
class TradingChainPostgresIntegrationTest {

    private static final BigDecimal ORDER_PRICE = new BigDecimal("100.00000000");
    private static final BigDecimal ORDER_QUANTITY = new BigDecimal("0.10000000");
    private static final BigDecimal VENUE_FILL_PRICE = new BigDecimal("123.45000000");
    private static final BigDecimal VENUE_FILL_QUANTITY = new BigDecimal("0.10000000");
    private static final Instant VENUE_FILL_TIME = Instant.parse("2026-08-30T00:00:03Z");
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-08-30T00:00:10Z"), ZoneOffset.UTC);

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private OkxRestReconcileService okxRestReconcileService;

    @Autowired
    private LedgerReconcileScheduler ledgerReconcileScheduler;

    @Autowired
    private RiskGate riskGate;

    @Autowired
    private KillSwitchService killSwitchService;

    @Autowired
    private OrderStateMachine orderStateMachine;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TradeLedgerPort tradeLedgerPort;

    @Autowired
    private TradingVenueGateway tradingVenueGateway;

    @Autowired
    private OkxExchangeAdapter okxExchangeAdapter;

    @Autowired
    private DeterministicFakeVenue fakeVenue;

    @BeforeEach
    void resetFakeVenue() {
        fakeVenue.reset();
    }

    @AfterAll
    static void restoreProxySelector() {
        ExchangeNoOutboundGuard.restoreDefault();
    }

    @Test
    void doesNotCreateTradeBeforeVenueReportsFill() {
        assertProductionComposition();
        ScenarioContext scenario = placeAcceptedOrder("negative");

        assertEquals(0, okxRestReconcileService.reconcileOnce(10));

        OrderRecord accepted = orderRepository.findByOrderId(scenario.order().orderId()).orElseThrow();
        assertEquals(OrderStatus.ACCEPTED, accepted.status());
        assertEquals(0L, count("SELECT COUNT(*) FROM trades WHERE order_id = ?", accepted.orderId()));
        assertEquals(0L, count("SELECT COUNT(*) FROM ledger_entries WHERE trace_id = ?", scenario.traceId()));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM positions WHERE account_id = ? AND symbol = ?",
                scenario.accountId(),
                accepted.symbol()
        ));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM account_snapshots WHERE account_id = ? AND currency = 'BTC'",
                scenario.accountId()
        ));
        assertEquals(accepted.externalOrderId(), fakeVenue.lastReconciliationExternalOrderId());
        assertEquals(1, fakeVenue.orderQueryCount());
        assertEquals(1, fakeVenue.fillQueryCount());
        assertEquals(0, fakeVenue.gatewayStatusQueryCount());
        assertEquals(0, ExchangeNoOutboundGuard.deniedSelections());
    }

    @Test
    void createsExactlyOneTradeFromVenueFillAndRemainsIdempotent() {
        assertProductionComposition();
        ScenarioContext scenario = placeAcceptedOrder("positive");
        OrderRecord accepted = scenario.order();
        String expectedExchangeTradeId = fakeVenue.reportFilled(accepted.externalOrderId());

        assertEquals(1, okxRestReconcileService.reconcileOnce(10));

        OrderRecord filled = orderRepository.findByOrderId(accepted.orderId()).orElseThrow();
        assertEquals(OrderStatus.FILLED, filled.status());
        assertEquals(1L, count("SELECT COUNT(*) FROM trades WHERE order_id = ?", filled.orderId()));

        var trade = tradeRepository.findByOrderId(filled.orderId()).orElseThrow();
        assertEquals(scenario.accountId(), trade.accountId());
        assertEquals(filled.orderId(), trade.orderId());
        assertEquals(filled.externalOrderId(), trade.externalOrderId());
        assertEquals(expectedExchangeTradeId, trade.exchangeTradeId());
        assertEquals("OKX", trade.exchange());
        assertEquals("BTC-USDT", trade.symbol());
        assertEquals(0, VENUE_FILL_PRICE.compareTo(trade.price()));
        assertEquals(0, VENUE_FILL_QUANTITY.compareTo(trade.qty()));
        assertFalse(ORDER_PRICE.equals(trade.price()));

        assertEquals(2L, count("SELECT COUNT(*) FROM ledger_entries WHERE ref_id = ?", trade.tradeId()));
        assertEquals(2L, count(
                "SELECT COUNT(*) FROM ledger_events le JOIN ledger_entries e ON e.entry_id = le.entry_id WHERE e.ref_id = ?",
                trade.tradeId()
        ));
        assertEquals(0, VENUE_FILL_QUANTITY.compareTo(decimal(
                "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
                scenario.accountId(),
                filled.symbol()
        )));
        assertEquals(0, VENUE_FILL_QUANTITY.compareTo(decimal(
                "SELECT balance FROM account_snapshots WHERE account_id = ? AND currency = 'BTC' "
                        + "ORDER BY ts DESC, snapshot_id DESC LIMIT 1",
                scenario.accountId()
        )));

        assertEquals(1L, count(
                "SELECT COUNT(*) FROM audit_logs WHERE trace_id = ? AND domain = 'RECONCILE' "
                        + "AND action = 'OKX_RECONCILE_COMPLETED' AND actor_id = ?",
                scenario.traceId(),
                filled.orderId()
        ));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM event_store WHERE trace_id = ? AND topic = ? "
                        + "AND event_type = 'TradeExecuted' AND key_value = ?",
                scenario.traceId(),
                TopicNames.TRADE_EVENT_V1,
                filled.clientOrderId()
        ));
        assertTrue(count("SELECT COUNT(*) FROM risk_events WHERE trace_id = ?", scenario.traceId()) >= 1);
        assertEquals(0, ledgerReconcileScheduler.reconcileOnce());

        long tradeCount = count("SELECT COUNT(*) FROM trades WHERE order_id = ?", filled.orderId());
        long ledgerCount = count("SELECT COUNT(*) FROM ledger_entries WHERE ref_id = ?", trade.tradeId());
        BigDecimal positionQuantity = decimal(
                "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
                scenario.accountId(),
                filled.symbol()
        );
        int orderQueries = fakeVenue.orderQueryCount();
        int fillQueries = fakeVenue.fillQueryCount();

        assertEquals(0, okxRestReconcileService.reconcileOnce(10));
        assertEquals(tradeCount, count("SELECT COUNT(*) FROM trades WHERE order_id = ?", filled.orderId()));
        assertEquals(ledgerCount, count("SELECT COUNT(*) FROM ledger_entries WHERE ref_id = ?", trade.tradeId()));
        assertEquals(0, positionQuantity.compareTo(decimal(
                "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
                scenario.accountId(),
                filled.symbol()
        )));
        assertEquals(orderQueries, fakeVenue.orderQueryCount());
        assertEquals(fillQueries, fakeVenue.fillQueryCount());
        assertEquals(filled.externalOrderId(), fakeVenue.lastReconciliationExternalOrderId());
        assertEquals(1, fakeVenue.placeCount());
        assertEquals(0, fakeVenue.gatewayStatusQueryCount());
        assertEquals(0, ExchangeNoOutboundGuard.deniedSelections());
    }

    private ScenarioContext placeAcceptedOrder(String caseName) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String traceId = "trc-l3-" + caseName + "-" + suffix;
        String clientOrderId = "coid-l3-" + caseName + "-" + suffix;
        Long accountId = insertAccount("gateaudit-l3-" + caseName + "-" + suffix);
        allowTestTransactionThroughRealKillSwitch(traceId);

        PlaceOrderRequest request = new PlaceOrderRequest(
                "req-l3-" + caseName + "-" + suffix,
                accountId,
                null,
                "OKX",
                "BTC-USDT",
                clientOrderId,
                accountId + ":" + clientOrderId,
                "gateaudit_l3_test",
                OrderSide.BUY,
                OrderType.LIMIT,
                ORDER_PRICE,
                ORDER_QUANTITY,
                "GTC",
                traceId
        );

        var placeResult = orderCommandService.placeOrder(request);
        assertEquals(OrderStatus.ACCEPTED, placeResult.status());
        assertFalse(placeResult.idempotentHit());
        assertEquals(1, fakeVenue.placeCount());

        OrderRecord accepted = orderRepository.findByOrderId(placeResult.orderId()).orElseThrow();
        assertEquals(accountId, accepted.accountId());
        assertEquals("OKX", accepted.venue());
        assertEquals("BTC-USDT", accepted.symbol());
        assertEquals(clientOrderId, accepted.clientOrderId());
        assertEquals(fakeVenue.externalOrderId(accepted.orderId()), accepted.externalOrderId());
        assertEquals(OrderStatus.ACCEPTED, accepted.status());
        return new ScenarioContext(traceId, accountId, accepted);
    }

    private void assertProductionComposition() {
        assertInstanceOf(PreTradeRiskService.class, riskGate);
        assertInstanceOf(InMemoryOrderStateMachine.class, orderStateMachine);
        assertEquals("JdbcOrderRepository", AopUtils.getTargetClass(orderRepository).getSimpleName());
        assertEquals("JdbcTradeRepository", AopUtils.getTargetClass(tradeRepository).getSimpleName());
        assertInstanceOf(TradeLedgerPostingService.class, tradeLedgerPort);
        assertSame(fakeVenue, tradingVenueGateway);
        assertSame(fakeVenue.okxAdapter(), okxExchangeAdapter);
        assertFalse(ProxySelector.getDefault() == null);
    }

    private Long insertAccount(String accountCode) {
        return jdbc.queryForObject(
                "INSERT INTO accounts (account_code, venue, status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id",
                Long.class,
                accountCode
        );
    }

    private void allowTestTransactionThroughRealKillSwitch(String traceId) {
        int updated = jdbc.update(
                "UPDATE kill_switch_states SET status='DISENGAGED', version=version+1, "
                        + "reason_code='GATEAUDIT_L3_TEST_FIXTURE', source='TEST_TRANSACTION', "
                        + "updated_at=CURRENT_TIMESTAMP, updated_by='GATEAUDIT_TEST', trace_id=? "
                        + "WHERE scope='GLOBAL_TRADING'",
                traceId
        );
        assertEquals(1, updated);
        assertEquals(KillSwitchStatus.DISENGAGED, killSwitchService.snapshot().status());
    }

    private long count(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        assertNotNull(result);
        return result;
    }

    private BigDecimal decimal(String sql, Object... args) {
        BigDecimal result = jdbc.queryForObject(sql, BigDecimal.class, args);
        assertNotNull(result);
        return result;
    }

    private record ScenarioContext(String traceId, Long accountId, OrderRecord order) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeVenueConfiguration {

        @Bean
        @Primary
        DeterministicFakeVenue deterministicFakeVenue() {
            return new DeterministicFakeVenue();
        }

        @Bean(name = "okxTradingAdapter")
        @Primary
        OkxExchangeAdapter deterministicOkxTradingAdapter(DeterministicFakeVenue fakeVenue) {
            return fakeVenue.okxAdapter();
        }
    }

    /** Test-only venue and transport script; every response is in-memory and deterministic. */
    static final class DeterministicFakeVenue implements TradingVenueGateway {

        private final AtomicInteger places = new AtomicInteger();
        private final AtomicInteger gatewayStatusQueries = new AtomicInteger();
        private final AtomicInteger cancels = new AtomicInteger();
        private final Map<String, VenueOrder> ordersByExternalId = new ConcurrentHashMap<>();
        private final Map<String, String> externalIdsByClientId = new ConcurrentHashMap<>();
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final ScriptedOkxHttpClient transport = new ScriptedOkxHttpClient(objectMapper, this);
        private final OkxExchangeAdapter okxAdapter = new OkxExchangeAdapter(new OkxExchangeAdapter.Dependencies(
                objectMapper,
                transport,
                new NoOutboundOkxInstrumentsCache(transport),
                TEST_CLOCK,
                "SIM"
        ));

        @Override
        public TradingPlaceGatewayResult placeOrder(OrderRecord order, PlaceOrderRequest request) {
            Objects.requireNonNull(order, "order must not be null");
            places.incrementAndGet();
            String externalOrderId = externalOrderId(order.orderId());
            VenueOrder venueOrder = new VenueOrder(
                    order.orderId(),
                    order.clientOrderId(),
                    externalOrderId,
                    order.symbol(),
                    OrderStatus.ACCEPTED
            );
            ordersByExternalId.put(externalOrderId, venueOrder);
            externalIdsByClientId.put(order.clientOrderId(), externalOrderId);
            return new TradingPlaceGatewayResult(
                    true,
                    externalOrderId,
                    OrderStatus.ACCEPTED.name(),
                    TradingGatewayResultCategory.ACCEPTED,
                    null,
                    Instant.parse("2026-08-30T00:00:00Z"),
                    "SIM"
            );
        }

        @Override
        public TradingCancelGatewayResult cancelOrder(
                OrderRecord order,
                com.guidinglight.nexusquant.trading.application.CancelOrderRequest request
        ) {
            cancels.incrementAndGet();
            return new TradingCancelGatewayResult(
                    false,
                    TradingGatewayResultCategory.FATAL_FAILURE,
                    null,
                    Instant.parse("2026-08-30T00:00:01Z"),
                    "SIM"
            );
        }

        @Override
        public TradingOrderStatusSnapshot getOrderStatus(OrderRecord order, String traceId) {
            gatewayStatusQueries.incrementAndGet();
            VenueOrder venueOrder = requireOrder(order.externalOrderId());
            return new TradingOrderStatusSnapshot(
                    venueOrder.externalOrderId(),
                    venueOrder.status().name(),
                    TradingGatewayResultCategory.SUCCESS,
                    null,
                    Instant.parse("2026-08-30T00:00:02Z"),
                    "SIM"
            );
        }

        String reportFilled(String externalOrderId) {
            VenueOrder current = requireOrder(externalOrderId);
            ordersByExternalId.put(externalOrderId, current.withStatus(OrderStatus.FILLED));
            return exchangeTradeId(current.orderId());
        }

        void reset() {
            places.set(0);
            gatewayStatusQueries.set(0);
            cancels.set(0);
            ordersByExternalId.clear();
            externalIdsByClientId.clear();
            transport.reset();
        }

        OkxExchangeAdapter okxAdapter() {
            return okxAdapter;
        }

        VenueOrder requireOrder(String externalOrderId) {
            VenueOrder order = ordersByExternalId.get(externalOrderId);
            if (order == null) {
                throw new AssertionError("unknown deterministic external order id: " + externalOrderId);
            }
            return order;
        }

        VenueOrder findByClientOrderId(String clientOrderId) {
            return requireOrder(externalIdsByClientId.get(clientOrderId));
        }

        int placeCount() {
            return places.get();
        }

        int gatewayStatusQueryCount() {
            return gatewayStatusQueries.get();
        }

        int orderQueryCount() {
            return transport.orderQueryCount();
        }

        int fillQueryCount() {
            return transport.fillQueryCount();
        }

        String lastReconciliationExternalOrderId() {
            return transport.lastExternalOrderId();
        }

        String externalOrderId(String orderId) {
            return "fake-l3-order-" + orderId;
        }

        String exchangeTradeId(String orderId) {
            return "fake-l3-fill-" + orderId;
        }
    }

    /** Real adapter input seam with no socket, credential, signer or external service dependency. */
    static final class ScriptedOkxHttpClient extends OkxHttpClient {

        private final ObjectMapper objectMapper;
        private final DeterministicFakeVenue venue;
        private final AtomicInteger orderQueries = new AtomicInteger();
        private final AtomicInteger fillQueries = new AtomicInteger();
        private volatile String lastExternalOrderId;

        ScriptedOkxHttpClient(ObjectMapper objectMapper, DeterministicFakeVenue venue) {
            super(
                    HttpClient.newHttpClient(),
                    objectMapper,
                    "http://127.0.0.1:1",
                    Duration.ofSeconds(1)
            );
            this.objectMapper = objectMapper;
            this.venue = venue;
        }

        @Override
        public JsonNode get(String requestPathWithQuery, String traceId) {
            if (requestPathWithQuery.startsWith("/api/v5/trade/order?")) {
                orderQueries.incrementAndGet();
                return orderResponse(requestPathWithQuery);
            }
            if (requestPathWithQuery.startsWith("/api/v5/trade/fills?")) {
                fillQueries.incrementAndGet();
                return fillResponse(requestPathWithQuery);
            }
            throw new AssertionError("unexpected OKX test transport request: " + requestPathWithQuery);
        }

        private JsonNode orderResponse(String requestPathWithQuery) {
            VenueOrder order = resolveOrder(requestPathWithQuery);
            lastExternalOrderId = order.externalOrderId();
            var item = objectMapper.createObjectNode();
            item.put("instId", order.symbol());
            item.put("clOrdId", order.clientOrderId());
            item.put("ordId", order.externalOrderId());
            item.put("state", order.status() == OrderStatus.FILLED ? "filled" : "live");
            item.put("px", ORDER_PRICE.toPlainString());
            item.put("sz", ORDER_QUANTITY.toPlainString());
            item.put("accFillSz", order.status() == OrderStatus.FILLED ? VENUE_FILL_QUANTITY.toPlainString() : "0");
            item.put("avgPx", order.status() == OrderStatus.FILLED ? VENUE_FILL_PRICE.toPlainString() : "");
            var envelope = objectMapper.createObjectNode();
            envelope.put("code", "0");
            envelope.put("msg", "");
            envelope.putArray("data").add(item);
            return envelope;
        }

        private JsonNode fillResponse(String requestPathWithQuery) {
            VenueOrder order = resolveOrder(requestPathWithQuery);
            lastExternalOrderId = order.externalOrderId();
            var envelope = objectMapper.createObjectNode();
            envelope.put("code", "0");
            envelope.put("msg", "");
            var data = envelope.putArray("data");
            if (order.status() == OrderStatus.FILLED) {
                var fill = objectMapper.createObjectNode();
                fill.put("tradeId", venue.exchangeTradeId(order.orderId()));
                fill.put("ordId", order.externalOrderId());
                fill.put("instId", order.symbol());
                fill.put("side", "buy");
                fill.put("fillPx", VENUE_FILL_PRICE.toPlainString());
                fill.put("fillSz", VENUE_FILL_QUANTITY.toPlainString());
                fill.put("fee", "0.00000000");
                fill.put("feeCcy", "USDT");
                fill.put("ts", VENUE_FILL_TIME.toEpochMilli());
                data.add(fill);
            }
            return envelope;
        }

        private VenueOrder resolveOrder(String requestPathWithQuery) {
            String externalOrderId = queryParam(requestPathWithQuery, "ordId");
            if (externalOrderId != null) {
                return venue.requireOrder(externalOrderId);
            }
            String clientOrderId = queryParam(requestPathWithQuery, "clOrdId");
            if (clientOrderId != null) {
                return venue.findByClientOrderId(clientOrderId);
            }
            throw new AssertionError("production adapter query omitted order identity: " + requestPathWithQuery);
        }

        private String queryParam(String requestPathWithQuery, String name) {
            String marker = name + "=";
            int start = requestPathWithQuery.indexOf(marker);
            if (start < 0) {
                return null;
            }
            start += marker.length();
            int end = requestPathWithQuery.indexOf('&', start);
            String encoded = end < 0
                    ? requestPathWithQuery.substring(start)
                    : requestPathWithQuery.substring(start, end);
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        }

        void reset() {
            orderQueries.set(0);
            fillQueries.set(0);
            lastExternalOrderId = null;
        }

        int orderQueryCount() {
            return orderQueries.get();
        }

        int fillQueryCount() {
            return fillQueries.get();
        }

        String lastExternalOrderId() {
            return lastExternalOrderId;
        }
    }

    static final class NoOutboundOkxInstrumentsCache extends OkxInstrumentsCache {

        NoOutboundOkxInstrumentsCache(OkxHttpClient transport) {
            super(transport, TEST_CLOCK, Duration.ofHours(1));
        }

        @Override
        public OkxInstrument getRequired(String instId, String traceId) {
            return new OkxInstrument(
                    instId,
                    new BigDecimal("0.01"),
                    new BigDecimal("0.001"),
                    new BigDecimal("0.001"),
                    "live"
            );
        }
    }

    private record VenueOrder(
            String orderId,
            String clientOrderId,
            String externalOrderId,
            String symbol,
            OrderStatus status
    ) {

        VenueOrder withStatus(OrderStatus targetStatus) {
            return new VenueOrder(orderId, clientOrderId, externalOrderId, symbol, targetStatus);
        }
    }

    static final class NoExchangeOutboundInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            ExchangeNoOutboundGuard.install();
        }
    }
}
