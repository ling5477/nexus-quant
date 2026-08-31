package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.service.TradeLedgerPostingService;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.PreTradeRiskService;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.scheduler.service.LedgerReconcileScheduler;
import com.guidinglight.nexusquant.scheduler.service.LedgerModuleTradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.TradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Autowired
    private FailOnceTradeLedgerGateway failOnceTradeLedgerGateway;

    @BeforeEach
    void resetFakeVenue() {
        fakeVenue.reset();
        failOnceTradeLedgerGateway.reset();
    }

    @AfterAll
    static void restoreProxySelector() {
        ExchangeNoOutboundGuard.restoreDefault();
    }

    @Test
    @Transactional
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
    @Transactional
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
            assertEquals(fillQueries + 1, fakeVenue.fillQueryCount());
        assertEquals(filled.externalOrderId(), fakeVenue.lastReconciliationExternalOrderId());
        assertEquals(1, fakeVenue.placeCount());
        assertEquals(0, fakeVenue.gatewayStatusQueryCount());
        assertEquals(0, ExchangeNoOutboundGuard.deniedSelections());
    }

    @Test
    @Transactional
    void keepsOneCanonicalOrderForRetryAndSeparatesIndependentEconomicActions() {
        assertProductionComposition();
        ScenarioContext first = placeAcceptedOrder("f003-canonical-order");
        int placeCountAfterFirst = fakeVenue.placeCount();

        var replay = orderCommandService.placeOrder(first.request());

        assertTrue(replay.idempotentHit());
        assertEquals(first.order().orderId(), replay.orderId());
        assertEquals(placeCountAfterFirst, fakeVenue.placeCount());
        assertEquals(1L, count(
                "SELECT count(*) FROM orders WHERE account_id=? AND client_order_id=?",
                first.accountId(), first.order().clientOrderId()));

        ScenarioContext second = placeAcceptedOrder("f003-independent-order");
        assertNotEquals(first.order().orderId(), second.order().orderId());
        assertNotEquals(first.order().clientOrderId(), second.order().clientOrderId());
        assertEquals(1L, count(
                "SELECT count(*) FROM orders WHERE account_id=? AND client_order_id=?",
                second.accountId(), second.order().clientOrderId()));
    }

    @Test
    void recoversLedgerAfterDurableTradeAndRemainsIdempotent() {
        assertProductionComposition();
        ScenarioContext scenario = placeAcceptedOrder("f004-recovery");
        OrderRecord accepted = scenario.order();
        failOnceTradeLedgerGateway.failNext(accepted.orderId());

        ScenarioContext unrelated = placeAcceptedOrder("f004-unrelated");
        fakeVenue.reportFilled(unrelated.order().externalOrderId());
        assertEquals(1, okxRestReconcileService.reconcileOnce(100));
        assertEquals(1, failOnceTradeLedgerGateway.remainingFailureCount());
        assertEquals(0, failOnceTradeLedgerGateway.attemptCount());

        fakeVenue.reportFilled(accepted.externalOrderId());

        try {
            assertThrows(
                    DeterministicLedgerPostingFailure.class,
                    () -> okxRestReconcileService.reconcileOnce(100)
            );
            assertEquals(0, failOnceTradeLedgerGateway.remainingFailureCount());

            OrderRecord filled = orderRepository.findByOrderId(accepted.orderId()).orElseThrow();
            var trade = tradeRepository.findByOrderId(filled.orderId()).orElseThrow();
            assertEquals(OrderStatus.FILLED, filled.status());
            assertEquals(1L, count("SELECT COUNT(*) FROM trades WHERE order_id = ?", filled.orderId()));
            assertEquals(0L, count("SELECT COUNT(*) FROM ledger_entries WHERE ref_id = ?", trade.tradeId()));
            assertEquals(0L, count(
                    "SELECT COUNT(*) FROM positions WHERE account_id = ? AND symbol = ?",
                    scenario.accountId(),
                    filled.symbol()
            ));
            assertEquals(0L, count(
                    "SELECT COUNT(*) FROM account_snapshots WHERE account_id = ? AND currency = 'BTC'",
                    scenario.accountId()
            ));
            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM event_store WHERE trace_id = ? AND topic = ? "
                            + "AND event_type = 'TradeExecuted' AND key_value = ?",
                    scenario.traceId(),
                    TopicNames.TRADE_EVENT_V1,
                    filled.clientOrderId()
            ));
            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM audit_logs WHERE trace_id = ? AND domain = 'RECONCILE' "
                            + "AND action = 'OKX_LEDGER_POST_FAILED'",
                    scenario.traceId()
            ));

            assertEquals(0, okxRestReconcileService.reconcileOnce(100));

            assertEquals(2, failOnceTradeLedgerGateway.attemptCount());
            assertEquals(1L, count("SELECT COUNT(*) FROM trades WHERE order_id = ?", filled.orderId()));
            assertEquals(2L, count("SELECT COUNT(*) FROM ledger_entries WHERE ref_id = ?", trade.tradeId()));
            assertEquals(2L, count(
                    "SELECT COUNT(*) FROM ledger_events le JOIN ledger_entries e ON e.entry_id = le.entry_id "
                            + "WHERE e.ref_id = ?",
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
                            + "AND action = 'OKX_LEDGER_RECOVERY_COMPLETED'",
                    scenario.traceId()
            ));
            long ledgerCountAfterRecovery = count(
                    "SELECT COUNT(*) FROM ledger_entries WHERE ref_id = ?",
                    trade.tradeId()
            );
            BigDecimal positionAfterRecovery = decimal(
                    "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
                    scenario.accountId(),
                    filled.symbol()
            );
            BigDecimal accountAfterRecovery = accountBalance(scenario);
            assertEquals(0, okxRestReconcileService.reconcileOnce(100));
            assertEquals(3, failOnceTradeLedgerGateway.attemptCount());
            assertEquals(1L, count("SELECT COUNT(*) FROM trades WHERE order_id = ?", filled.orderId()));
            assertEquals(ledgerCountAfterRecovery, count(
                    "SELECT COUNT(*) FROM ledger_entries WHERE ref_id = ?",
                    trade.tradeId()
            ));
            assertEquals(0, positionAfterRecovery.compareTo(decimal(
                    "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
                    scenario.accountId(),
                    filled.symbol()
            )));
            assertEquals(0, accountAfterRecovery.compareTo(accountBalance(scenario)));
            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM audit_logs WHERE trace_id = ? AND domain = 'RECONCILE' "
                            + "AND action = 'OKX_LEDGER_RECOVERY_COMPLETED'",
                    scenario.traceId()
            ));
            assertEquals(0, ExchangeNoOutboundGuard.deniedSelections());
        } finally {
            retireTestOrders(accepted.orderId(), unrelated.order().orderId());
            restoreTestKillSwitch(scenario.traceId());
        }
    }

    @Test
    @Transactional
    void processesMultipleVenueFillsAsIndependentTrades() {
        ScenarioContext scenario = placeAcceptedOrder("f004-multifill");
        VenueFill fillA = venueFill(scenario, "A", "120.00000000", "0.04000000", 4);
        VenueFill fillB = venueFill(scenario, "B", "125.00000000", "0.06000000", 5);
        fakeVenue.reportFills(scenario.order().externalOrderId(), List.of(fillA, fillB));

        assertEquals(2, okxRestReconcileService.reconcileOnce(100));

        PaperTradeRecord tradeA = tradeByExchangeId(fillA.exchangeTradeId());
        PaperTradeRecord tradeB = tradeByExchangeId(fillB.exchangeTradeId());
        assertEquals(1L, count("SELECT COUNT(*) FROM trades WHERE exchange_trade_id=?", fillA.exchangeTradeId()));
        assertEquals(1L, count("SELECT COUNT(*) FROM trades WHERE exchange_trade_id=?", fillB.exchangeTradeId()));
        assertLedgerComplete(tradeA);
        assertLedgerComplete(tradeB);
        assertEquals(0, ORDER_QUANTITY.compareTo(positionQuantity(scenario)));
        assertEquals(0, ORDER_QUANTITY.compareTo(accountBalance(scenario)));

        assertEquals(0, okxRestReconcileService.reconcileOnce(100));
        assertLedgerComplete(tradeA);
        assertLedgerComplete(tradeB);
        assertEquals(0, ORDER_QUANTITY.compareTo(positionQuantity(scenario)));
    }

    @Test
    @Transactional
    void recoversOlderNonLatestTradeWithoutChangingCompletedLatestTrade() {
        ScenarioContext scenario = placeAcceptedOrder("f004-old-trade");
        VenueFill fillA = venueFill(scenario, "OLD", "120.00000000", "0.04000000", 4);
        VenueFill fillB = venueFill(scenario, "LATEST", "125.00000000", "0.06000000", 5);
        PaperTradeRecord tradeA = insertDurableTrade(scenario, fillA);
        PaperTradeRecord tradeB = insertDurableTrade(scenario, fillB);
        postCanonicalLedger(scenario.order(), tradeB);
        long latestLedgerBefore = ledgerEntryCount(tradeB);
        assertEquals(tradeB.tradeId(), tradeRepository.findByOrderId(scenario.order().orderId()).orElseThrow().tradeId());
        assertEquals(0L, ledgerEntryCount(tradeA));
        fakeVenue.reportFills(scenario.order().externalOrderId(), List.of());

        assertEquals(0, okxRestReconcileService.reconcileOnce(100));

        assertLedgerComplete(tradeA);
        assertEquals(latestLedgerBefore, ledgerEntryCount(tradeB));
        assertEquals(2L, count("SELECT COUNT(*) FROM trades WHERE order_id=?", scenario.order().orderId()));
        assertEquals(0, ORDER_QUANTITY.compareTo(positionQuantity(scenario)));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM audit_logs WHERE trace_id=? "
                        + "AND action='OKX_LEDGER_RECOVERY_COMPLETED' AND detail_json->>'trade_id'=?",
                scenario.traceId(),
                tradeA.tradeId()
        ));
    }

    @Test
    @Transactional
    void processesNewFillWhenAnotherTradeAlreadyExists() {
        ScenarioContext scenario = placeAcceptedOrder("f004-existing-new");
        VenueFill fillA = venueFill(scenario, "EXISTING", "120.00000000", "0.04000000", 4);
        VenueFill fillB = venueFill(scenario, "NEW", "125.00000000", "0.06000000", 5);
        PaperTradeRecord tradeA = insertDurableTrade(scenario, fillA);
        postCanonicalLedger(scenario.order(), tradeA);
        fakeVenue.reportFills(scenario.order().externalOrderId(), List.of(fillB));

        assertEquals(1, okxRestReconcileService.reconcileOnce(100));

        PaperTradeRecord tradeB = tradeByExchangeId(fillB.exchangeTradeId());
        assertEquals(2L, count("SELECT COUNT(*) FROM trades WHERE order_id=?", scenario.order().orderId()));
        assertLedgerComplete(tradeA);
        assertLedgerComplete(tradeB);
        assertEquals(0, ORDER_QUANTITY.compareTo(positionQuantity(scenario)));
        assertEquals(0, ORDER_QUANTITY.compareTo(accountBalance(scenario)));
    }

    @Test
    @Transactional
    void rejectsRecoveryWhenDurableTradeDoesNotBelongToOwningOrder() {
        ScenarioContext scenario = placeAcceptedOrder("f004-identity-mismatch");
        Long otherAccountId = insertAccount("gateaudit-l3-f004-other-" + UUID.randomUUID());
        VenueFill fill = venueFill(scenario, "MISMATCH", "123.00000000", "0.10000000", 4);
        PaperTradeRecord mismatchedTrade = new PaperTradeRecord(
                "trd-f004-mismatch-" + UUID.randomUUID(),
                scenario.order().orderId(),
                otherAccountId,
                scenario.order().symbol(),
                scenario.order().venue(),
                scenario.order().externalOrderId(),
                fill.exchangeTradeId(),
                fill.price(),
                fill.quantity(),
                BigDecimal.ZERO,
                "USDT",
                scenario.traceId(),
                fill.tradeTs()
        );
        tradeRepository.insert(mismatchedTrade);
        fakeVenue.reportFills(scenario.order().externalOrderId(), List.of());

        assertThrows(IllegalStateException.class, () -> okxRestReconcileService.reconcileOnce(100));

        assertEquals(0L, ledgerEntryCount(mismatchedTrade));
        assertEquals(0L, count("SELECT COUNT(*) FROM positions WHERE account_id IN (?, ?)", scenario.accountId(), otherAccountId));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM account_snapshots WHERE account_id IN (?, ?)",
                scenario.accountId(),
                otherAccountId
        ));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM audit_logs WHERE trace_id=? "
                        + "AND action='OKX_LEDGER_RECOVERY_IDENTITY_MISMATCH'",
                scenario.traceId()
        ));

        assertThrows(IllegalStateException.class, () -> okxRestReconcileService.reconcileOnce(100));
        assertEquals(0L, ledgerEntryCount(mismatchedTrade));
        assertEquals(2L, count(
                "SELECT COUNT(*) FROM audit_logs WHERE trace_id=? "
                        + "AND action='OKX_LEDGER_RECOVERY_IDENTITY_MISMATCH'",
                scenario.traceId()
        ));
    }

    private VenueFill venueFill(
            ScenarioContext scenario,
            String suffix,
            String price,
            String quantity,
            long secondOffset
    ) {
        return new VenueFill(
                "fake-l3-fill-" + suffix + "-" + scenario.order().orderId(),
                new BigDecimal(price),
                new BigDecimal(quantity),
                VENUE_FILL_TIME.plusSeconds(secondOffset)
        );
    }

    private PaperTradeRecord insertDurableTrade(ScenarioContext scenario, VenueFill fill) {
        PaperTradeRecord trade = new PaperTradeRecord(
                "trd-f004-" + UUID.randomUUID(),
                scenario.order().orderId(),
                scenario.accountId(),
                scenario.order().symbol(),
                scenario.order().venue(),
                scenario.order().externalOrderId(),
                fill.exchangeTradeId(),
                fill.price(),
                fill.quantity(),
                BigDecimal.ZERO,
                "USDT",
                scenario.traceId(),
                fill.tradeTs()
        );
        tradeRepository.insert(trade);
        return trade;
    }

    private void postCanonicalLedger(OrderRecord order, PaperTradeRecord trade) {
        LedgerPostingResult result = tradeLedgerPort.postTrade(new TradeLedgerRequest(
                trade.tradeId(),
                trade.orderId(),
                trade.accountId(),
                trade.symbol(),
                OrderSide.valueOf(order.side()),
                trade.price(),
                trade.qty(),
                trade.fee(),
                trade.feeCurrency(),
                trade.traceId(),
                trade.ts()
        ));
        assertTrue(result.posted());
    }

    private PaperTradeRecord tradeByExchangeId(String exchangeTradeId) {
        return tradeRepository.findByExchangeAndExchangeTradeId("OKX", exchangeTradeId).orElseThrow();
    }

    private void assertLedgerComplete(PaperTradeRecord trade) {
        assertEquals(2L, ledgerEntryCount(trade));
        assertEquals(2L, count(
                "SELECT COUNT(*) FROM ledger_events le JOIN ledger_entries e ON e.entry_id=le.entry_id "
                        + "WHERE e.ref_id=?",
                trade.tradeId()
        ));
    }

    private long ledgerEntryCount(PaperTradeRecord trade) {
        return count("SELECT COUNT(*) FROM ledger_entries WHERE ref_id=?", trade.tradeId());
    }

    private BigDecimal positionQuantity(ScenarioContext scenario) {
        return decimal(
                "SELECT qty FROM positions WHERE account_id=? AND symbol=?",
                scenario.accountId(),
                scenario.order().symbol()
        );
    }

    private BigDecimal accountBalance(ScenarioContext scenario) {
        return decimal(
                "SELECT balance FROM account_snapshots WHERE account_id=? AND currency='BTC' "
                        + "ORDER BY ts DESC, snapshot_id DESC LIMIT 1",
                scenario.accountId()
        );
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

        int placeCountBefore = fakeVenue.placeCount();
        var placeResult = orderCommandService.placeOrder(request);
        assertEquals(OrderStatus.ACCEPTED, placeResult.status());
        assertFalse(placeResult.idempotentHit());
        assertEquals(placeCountBefore + 1, fakeVenue.placeCount());

        OrderRecord accepted = orderRepository.findByOrderId(placeResult.orderId()).orElseThrow();
        assertEquals(accountId, accepted.accountId());
        assertEquals("OKX", accepted.venue());
        assertEquals("BTC-USDT", accepted.symbol());
        assertEquals(clientOrderId, accepted.clientOrderId());
        assertEquals(fakeVenue.externalOrderId(accepted.orderId()), accepted.externalOrderId());
        assertEquals(OrderStatus.ACCEPTED, accepted.status());
        return new ScenarioContext(traceId, accountId, accepted, request);
    }

    private void assertProductionComposition() {
        assertInstanceOf(PreTradeRiskService.class, riskGate);
        assertInstanceOf(InMemoryOrderStateMachine.class, orderStateMachine);
        assertEquals("JdbcOrderRepository", AopUtils.getTargetClass(orderRepository).getSimpleName());
        assertEquals("JdbcTradeRepository", AopUtils.getTargetClass(tradeRepository).getSimpleName());
        assertInstanceOf(TradeLedgerPostingService.class, tradeLedgerPort);
        assertInstanceOf(LedgerModuleTradeLedgerGateway.class, failOnceTradeLedgerGateway.realDelegate());
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
                        + "updated_at=CURRENT_TIMESTAMP - INTERVAL '1 second', "
                        + "updated_by='GATEAUDIT_TEST', trace_id=? "
                        + "WHERE scope='GLOBAL_TRADING'",
                traceId
        );
        assertEquals(1, updated);
        assertEquals(KillSwitchStatus.DISENGAGED, killSwitchService.snapshot().status());
    }

    private void restoreTestKillSwitch(String traceId) {
        int updated = jdbc.update(
                "UPDATE kill_switch_states SET status='ENGAGED', version=version+1, "
                        + "reason_code='GATEAUDIT_F004_TEST_COMPLETE', source='TEST_CLEANUP', "
                        + "updated_at=CURRENT_TIMESTAMP - INTERVAL '1 second', "
                        + "updated_by='GATEAUDIT_TEST', trace_id=? "
                        + "WHERE scope='GLOBAL_TRADING'",
                traceId
        );
        assertEquals(1, updated);
    }

    private void retireTestOrders(String... orderIds) {
        for (String orderId : orderIds) {
            assertEquals(1, jdbc.update(
                    "UPDATE orders SET status='CANCELLED', reason='GATEAUDIT_TEST_CLEANUP', "
                            + "updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
                    orderId
            ));
        }
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

    private record ScenarioContext(
            String traceId,
            Long accountId,
            OrderRecord order,
            PlaceOrderRequest request
    ) {
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

        @Bean
        @Primary
        FailOnceTradeLedgerGateway failOnceTradeLedgerGateway(LedgerModuleTradeLedgerGateway delegate) {
            return new FailOnceTradeLedgerGateway(delegate);
        }
    }

    /** Test-only deterministic failure after durable Trade and before the real ledger transaction. */
    static final class FailOnceTradeLedgerGateway implements TradeLedgerGateway {

        private final LedgerModuleTradeLedgerGateway delegate;
        private final AtomicInteger remainingFailures = new AtomicInteger();
        private final AtomicInteger targetAttempts = new AtomicInteger();
        private final AtomicReference<String> targetOrderId = new AtomicReference<>();

        FailOnceTradeLedgerGateway(LedgerModuleTradeLedgerGateway delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public LedgerPostingResult postTrade(TradeLedgerRequest request) {
            if (!Objects.equals(targetOrderId.get(), request.orderId())) {
                return delegate.postTrade(request);
            }
            targetAttempts.incrementAndGet();
            if (remainingFailures.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0) {
                throw new DeterministicLedgerPostingFailure();
            }
            return delegate.postTrade(request);
        }

        void failNext(String orderId) {
            targetOrderId.set(Objects.requireNonNull(orderId, "orderId must not be null"));
            remainingFailures.set(1);
        }

        int attemptCount() {
            return targetAttempts.get();
        }

        int remainingFailureCount() {
            return remainingFailures.get();
        }

        LedgerModuleTradeLedgerGateway realDelegate() {
            return delegate;
        }

        void reset() {
            remainingFailures.set(0);
            targetAttempts.set(0);
            targetOrderId.set(null);
        }
    }

    static final class DeterministicLedgerPostingFailure extends RuntimeException {
        DeterministicLedgerPostingFailure() {
            super("deterministic F-004 ledger posting failure");
        }
    }

    /** Test-only venue and transport script; every response is in-memory and deterministic. */
    static final class DeterministicFakeVenue implements TradingVenueGateway {

        private final AtomicInteger places = new AtomicInteger();
        private final AtomicInteger gatewayStatusQueries = new AtomicInteger();
        private final AtomicInteger cancels = new AtomicInteger();
        private final Map<String, VenueOrder> ordersByExternalId = new ConcurrentHashMap<>();
        private final Map<String, String> externalIdsByClientId = new ConcurrentHashMap<>();
        private final Map<String, List<VenueFill>> fillsByExternalId = new ConcurrentHashMap<>();
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
            String exchangeTradeId = exchangeTradeId(current.orderId());
            reportFills(externalOrderId, List.of(new VenueFill(
                    exchangeTradeId,
                    VENUE_FILL_PRICE,
                    VENUE_FILL_QUANTITY,
                    VENUE_FILL_TIME
            )));
            return exchangeTradeId;
        }

        void reportFills(String externalOrderId, List<VenueFill> fills) {
            VenueOrder current = requireOrder(externalOrderId);
            ordersByExternalId.put(externalOrderId, current.withStatus(OrderStatus.FILLED));
            fillsByExternalId.put(externalOrderId, List.copyOf(fills));
        }

        List<VenueFill> fillsFor(String externalOrderId) {
            return fillsByExternalId.getOrDefault(externalOrderId, List.of());
        }

        void reset() {
            places.set(0);
            gatewayStatusQueries.set(0);
            cancels.set(0);
            ordersByExternalId.clear();
            externalIdsByClientId.clear();
            fillsByExternalId.clear();
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
            List<VenueFill> fills = venue.fillsFor(order.externalOrderId());
            BigDecimal filledQuantity = fills.stream()
                    .map(VenueFill::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.put("accFillSz", filledQuantity.toPlainString());
            item.put("avgPx", fills.isEmpty() ? "" : fills.getFirst().price().toPlainString());
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
            for (VenueFill venueFill : venue.fillsFor(order.externalOrderId())) {
                var fill = objectMapper.createObjectNode();
                fill.put("tradeId", venueFill.exchangeTradeId());
                fill.put("ordId", order.externalOrderId());
                fill.put("instId", order.symbol());
                fill.put("side", "buy");
                fill.put("fillPx", venueFill.price().toPlainString());
                fill.put("fillSz", venueFill.quantity().toPlainString());
                fill.put("fee", "0.00000000");
                fill.put("feeCcy", "USDT");
                fill.put("ts", venueFill.tradeTs().toEpochMilli());
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

    private record VenueFill(
            String exchangeTradeId,
            BigDecimal price,
            BigDecimal quantity,
            Instant tradeTs
    ) {
    }

    static final class NoExchangeOutboundInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            ExchangeNoOutboundGuard.install();
        }
    }
}
