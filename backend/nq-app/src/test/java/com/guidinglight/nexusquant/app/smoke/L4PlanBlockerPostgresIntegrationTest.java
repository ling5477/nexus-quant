package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.contracts.model.*;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.trading.application.*;
import com.guidinglight.nexusquant.trading.application.port.*;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Three temporary canonical defect reproductions (two findings) and one normal regression, not L4 qualification.
 * Defect assertions intentionally describe the unfixed baseline. No production seams are added.
 * A known-defect-reproduction PASS means the defect was reproduced, never correctness acceptance.
 * Lifecycle owner and post-fix inversion are bound in the plan JSON reproductionContract.cases.
 * Run only against the task-owned disposable PostgreSQL database.
 */
@SpringBootTest(classes = {NexusQuantApplication.class,
        L4PlanBlockerPostgresIntegrationTest.Configuration.class}, properties = {
        "spring.main.allow-bean-definition-overriding=true", "spring.task.scheduling.enabled=false",
        "nq.runtime.trading-components.enabled=true", "nq.validation-operations.scheduler.enabled=false",
        "nq.okx.recovery.enabled=false", "nq.okx.ws.enabled=false", "nq.binance.ws.enabled=false",
        "nq.instrument.catalog-sync.enabled=false", "nq.env-safety.live-enabled=false",
        "nq.env-safety.ai-enabled=false", "nq.env-safety.dh-runtime-enabled=false",
        "nq.env-safety.real-provider-enabled=false", "nq.env-safety.real-client-enabled=false",
        "nq.env-safety.real-exchange-enabled=false", "nq.env-safety.no-outbound=true"
})
@ActiveProfiles("local")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "nq.l4.blockers.enabled", matches = "true")
@ContextConfiguration(initializers = TradingChainPostgresIntegrationTest.NoExchangeOutboundInitializer.class)
class L4PlanBlockerPostgresIntegrationTest {
    @Autowired OrderCommandService commands;
    @Autowired OrderRepository orders;
    @Autowired OkxRestReconcileService reconcile;
    @Autowired KillSwitchService kill;
    @Autowired ControlledGateway gateway;
    @MockitoSpyBean JdbcTemplate jdbc;
    private final List<String> updates = new CopyOnWriteArrayList<>();
    private final List<String> createdOrders = new CopyOnWriteArrayList<>();

    @BeforeEach void prepare() {
        String url = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                c -> c.getMetaData().getURL());
        assertNotNull(url);
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.contains("/nq_l4_blocker"),
                "This characterization requires the dedicated disposable database");
        gateway.reset();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            Object rows = call.callRealMethod();
            if (sql.startsWith("UPDATE orders SET status")) {
                updates.add(sql + " affected=" + rows + " thread=" + Thread.currentThread().getName());
            }
            return rows;
        }).when(jdbc).update(anyString(), any(Object[].class));
        jdbc.update("UPDATE kill_switch_states SET status='DISENGAGED',version=version+1,"
                + "reason_code='L4_TEST_SETUP',source='TEST_FIXTURE',updated_at=CURRENT_TIMESTAMP - INTERVAL '1 second',"
                + "updated_by='l4-test',trace_id='l4-test' WHERE scope='GLOBAL_TRADING'");
        assertEquals(KillSwitchStatus.DISENGAGED, kill.snapshot().status());
    }

    @AfterEach void cleanup() {
        gateway.release.countDown();
        for (String id : createdOrders) {
            jdbc.update("UPDATE orders SET status='CANCELLED',reason='L4_TEST_CLEANUP' WHERE order_id=?", id);
        }
        kill.engage(kill.snapshot().version(), "L4_TEST_COMPLETE", "l4-test", "l4-test");
    }

    @AfterAll static void restoreProxy() { ExchangeNoOutboundGuard.restoreDefault(); }

    /**
     * KNOWN_DEFECT_REPRODUCTION R02 / P1-2, owner C1: PASS observes stale PLACE ACK overwriting FILLED.
     * At C1 fix, invert to surviving FILLED and explicit stale-write rejection; this is not a business invariant.
     */
    @Tag("known-defect-reproduction")
    @Test void stalePlaceAckOverwritesCommittedFilledThroughRealSpringTransactions() throws Exception {
        var request = request("stale-place");
        gateway.pausePlace = true;
        var executor = Executors.newSingleThreadExecutor();
        try {
            var t1 = executor.submit(() -> commands.placeOrder(request));
            assertTrue(gateway.reached.await(15, TimeUnit.SECONDS));
            var old = gateway.inFlight.get();
            createdOrders.add(old.orderId());
            assertEquals(OrderStatus.SENT, current(old).status());
            gateway.venue.reportFilled(gateway.venue.externalOrderId(old.orderId()));
            assertEquals(1, reconcile.reconcileOnce(100));
            assertEquals(OrderStatus.FILLED, current(old).status());
            assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", old.orderId()));
            gateway.release.countDown();
            assertEquals(OrderStatus.ACCEPTED, t1.get(15, TimeUnit.SECONDS).status());
            assertEquals(OrderStatus.ACCEPTED, current(old).status());
            assertTrue(updates.stream().anyMatch(s -> s.contains("affected=1") && s.contains("pool-")));
            assertEquals(2L, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", old.traceId()));
            System.out.println("L4-P1-2 PLACE: T2 FILLED committed; stale T1 ACK -> ACCEPTED; " + updates);
        } finally {
            gateway.release.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    /**
     * KNOWN_DEFECT_REPRODUCTION R03 / P1-2, owner C1: PASS observes stale CANCEL ACK overwriting FILLED.
     * At C1 fix, invert to surviving FILLED and explicit stale-write rejection; this is not a business invariant.
     */
    @Tag("known-defect-reproduction")
    @Test void staleCancelAckOverwritesCommittedFilledThroughRealSpringTransactions() throws Exception {
        var order = place(request("stale-cancel"));
        gateway.pauseCancel = true;
        var executor = Executors.newSingleThreadExecutor();
        try {
            var t1 = executor.submit(() -> commands.cancelOrder(cancel(order)));
            assertTrue(gateway.reached.await(15, TimeUnit.SECONDS));
            assertEquals(OrderStatus.CANCEL_REQUESTED, current(order).status());
            gateway.venue.reportFilled(order.externalOrderId());
            assertEquals(1, reconcile.reconcileOnce(100));
            assertEquals(OrderStatus.FILLED, current(order).status());
            gateway.release.countDown();
            assertEquals(OrderStatus.CANCELLED, t1.get(15, TimeUnit.SECONDS).status());
            assertEquals(OrderStatus.CANCELLED, current(order).status());
            assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", order.orderId()));
            assertTrue(updates.stream().anyMatch(s -> s.contains("affected=1") && s.contains("pool-")));
            System.out.println("L4-P1-2 CANCEL: T2 FILLED committed; stale T1 ACK -> CANCELLED; " + updates);
        } finally {
            gateway.release.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    /**
     * KNOWN_DEFECT_REPRODUCTION R04 / P1-3, owner C2: PASS observes missing fill/ledger recovery.
     * At C2 fix, invert the negative branch to bounded idempotent backfill, retaining the positive control.
     * The empty Trade/Ledger assertion is temporary evidence, never an accepted business invariant.
     */
    @Tag("known-defect-reproduction")
    @Test void cancelledOrderWithVenueFillIsExcludedWhileFilledPositiveControlRecovers() {
        var cancelled = place(request("blind-spot"));
        commands.cancelOrder(cancel(cancelled));
        gateway.venue.reportFilled(cancelled.externalOrderId());
        int queries = gateway.venue.fillQueryCount();
        for (int pass = 0; pass < 2; pass++) assertEquals(0, reconcile.reconcileOnce(100));
        assertEquals(queries, gateway.venue.fillQueryCount());
        assertNoFillPersistence(cancelled);
        assertEquals(OrderStatus.CANCELLED, current(cancelled).status());
        var control = place(request("scan-control"));
        gateway.venue.reportFilled(control.externalOrderId());
        assertEquals(1, reconcile.reconcileOnce(100));
        assertEquals(OrderStatus.FILLED, current(control).status());
        assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", control.orderId()));
        assertEquals(2L, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", control.traceId()));
        assertNoFillPersistence(cancelled);
        System.out.println("L4-P1-3 CANCELLED: scans=2 fillQueries=0 Trade=0 Ledger=0; ACCEPTED positive control -> FILLED/1/2");
    }

    /** NORMAL_REGRESSION R05 / current recovery positive control, owner C2: retain rejection of new PLACE and kill-preserving recovery. */
    @Tag("normal-regression")
    @Test void engagedKillRejectsNewPlaceButAllowsOrdinaryReadAndLedgerConvergence() {
        var order = place(request("kill-recovery"));
        gateway.venue.reportFilled(order.externalOrderId());
        kill.engage(kill.snapshot().version(), "L4_TEST_ENGAGE", "l4-test", order.traceId());
        assertEquals(KillSwitchStatus.ENGAGED, kill.snapshot().status());
        var rejected = commands.placeOrder(request("kill-new"));
        createdOrders.add(rejected.orderId());
        assertEquals(OrderStatus.RISK_REJECTED, rejected.status());
        assertEquals(1, gateway.venue.placeCount());
        assertEquals(1, reconcile.reconcileOnce(100));
        assertEquals(OrderStatus.FILLED, current(order).status());
        assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", order.orderId()));
        assertEquals(2L, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", order.traceId()));
        assertEquals(KillSwitchStatus.ENGAGED, kill.snapshot().status());
        System.out.println("L4-R05 ordinary positive control: kill=ENGAGED newPLACE=RISK_REJECTED recovery=FILLED/Trade1/Ledger2");
    }

    private PlaceOrderRequest request(String name) {
        UUID intentId = UUID.randomUUID();
        String id = UUID.randomUUID().toString().replace("-", "");
        Long account = jdbc.queryForObject("INSERT INTO accounts(account_code,venue,status) VALUES (?,'OKX','ACTIVE') RETURNING account_id",
                Long.class, "l4-" + id);
        String clientId = com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder.stableClientOrderId(intentId);
        return new PlaceOrderRequest("l4-" + name + id, account, null, "OKX", "BTC-USDT", clientId,
                "l4" + id, "L4_TEST", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("0.1"), "GTC", "l4-" + id);
    }
    private OrderRecord place(PlaceOrderRequest request) {
        var result = commands.placeOrder(request);
        createdOrders.add(result.orderId());
        assertEquals(OrderStatus.ACCEPTED, result.status());
        return orders.findByOrderId(result.orderId()).orElseThrow();
    }
    private CancelOrderRequest cancel(OrderRecord order) {
        return new CancelOrderRequest(order.orderId(), order.accountId(), order.clientOrderId(), "L4_TEST_CANCEL", order.traceId());
    }
    private OrderRecord current(OrderRecord order) { return orders.findByOrderId(order.orderId()).orElseThrow(); }
    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void assertNoFillPersistence(OrderRecord order) {
        assertEquals(0L, count("SELECT count(*) FROM trades WHERE order_id=?", order.orderId()));
        assertEquals(0L, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", order.traceId()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean TradingChainPostgresIntegrationTest.DeterministicFakeVenue deterministicFakeVenue() {
            return new TradingChainPostgresIntegrationTest.DeterministicFakeVenue();
        }
        @Bean(name = "okxTradingAdapter") @Primary
        com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter okxTradingAdapter(
                TradingChainPostgresIntegrationTest.DeterministicFakeVenue venue) { return venue.okxAdapter(); }
        @Bean @Primary ControlledGateway controlledGateway(TradingChainPostgresIntegrationTest.DeterministicFakeVenue venue) {
            return new ControlledGateway(venue);
        }
    }
    static final class ControlledGateway implements TradingVenueGateway {
        final TradingChainPostgresIntegrationTest.DeterministicFakeVenue venue;
        volatile boolean pausePlace, pauseCancel;
        volatile CountDownLatch reached, release;
        final AtomicReference<OrderRecord> inFlight = new AtomicReference<>();
        ControlledGateway(TradingChainPostgresIntegrationTest.DeterministicFakeVenue venue) { this.venue = venue; reset(); }
        void reset() {
            venue.reset(); pausePlace = false; pauseCancel = false;
            reached = new CountDownLatch(1); release = new CountDownLatch(1); inFlight.set(null);
        }
        private void barrier(OrderRecord order) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "provider action must be outside Order transaction");
            inFlight.set(order); reached.countDown();
            try { assertTrue(release.await(15, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        public TradingPlaceGatewayResult placeOrder(OrderRecord order, PlaceOrderRequest request) {
            var ack = venue.placeOrder(order, request);
            if (pausePlace) barrier(order);
            return ack;
        }
        public TradingCancelGatewayResult cancelOrder(OrderRecord order, CancelOrderRequest request) {
            if (pauseCancel) barrier(order);
            return new TradingCancelGatewayResult(true, TradingGatewayResultCategory.ACCEPTED, null, Instant.now(), "SIM");
        }
        public TradingOrderStatusSnapshot getOrderStatus(OrderRecord order, String trace) { return venue.getOrderStatus(order, trace); }
    }
}
