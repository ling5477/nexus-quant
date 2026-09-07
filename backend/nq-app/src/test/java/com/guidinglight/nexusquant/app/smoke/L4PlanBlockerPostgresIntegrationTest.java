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
 * C1 使用真实事务与确定性 barrier 验证 versioned OCC；C2 仍保留已知缺陷复现。
 * 测试只运行于任务专用临时 PostgreSQL；不代表 L4 qualification 或 C2 correctness。
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
    @MockitoSpyBean OrderCommandWriteService writes;
    private final List<String> updates = new CopyOnWriteArrayList<>();
    private final List<CasObservation> cas = new CopyOnWriteArrayList<>();
    private final List<String> createdOrders = new CopyOnWriteArrayList<>();

    @BeforeEach void prepare() {
        String url = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                c -> c.getMetaData().getURL());
        assertNotNull(url);
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.contains("/nq_l4_blocker"),
                "This characterization requires the dedicated disposable database");
        assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("16."));
        gateway.reset();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            Object rows = call.callRealMethod();
            if (sql.startsWith("UPDATE orders SET status")) {
                updates.add(sql + " affected=" + rows + " thread=" + Thread.currentThread().getName());
                if (sql.contains("AND version = ?")) {
                    Object[] args = (Object[]) call.getRawArguments()[1];
                    cas.add(new CasObservation((String) args[3], (String) args[4], (long) args[5],
                            (String) args[0], (int) rows, TransactionSynchronizationManager.isActualTransactionActive()));
                }
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
        gateway.oldRelease.countDown(); gateway.newRelease.countDown();
        for (String id : createdOrders) {
            jdbc.update("UPDATE orders SET status='CANCELLED',reason='L4_TEST_CLEANUP' WHERE order_id=?", id);
        }
        kill.engage(kill.snapshot().version(), "L4_TEST_COMPLETE", "l4-test", "l4-test");
    }

    @AfterAll static void restoreProxy() { ExchangeNoOutboundGuard.restoreDefault(); }

    /** R02：较新 FILLED 提交后，旧 PLACE ACK 不得取得迁移所有权。 */
    @Test void stalePlaceAckPreservesCommittedFilledThroughRealSpringTransactions() throws Exception {
        var request = request("stale-place");
        gateway.pausePlace = true;
        var executor = Executors.newSingleThreadExecutor();
        try {
            var t1 = executor.submit(() -> commands.placeOrder(request));
            if (!gateway.reached.await(15, TimeUnit.SECONDS)) {
                t1.get(1, TimeUnit.SECONDS);
                fail("PLACE did not reach gateway barrier");
            }
            var old = gateway.inFlight.get();
            createdOrders.add(old.orderId());
            assertEquals(OrderStatus.SENT, current(old).status());
            gateway.venue.reportFilled(gateway.venue.externalOrderId(old.orderId()));
            assertEquals(1, reconcile.reconcileOnce(100));
            assertEquals(OrderStatus.FILLED, current(old).status());
            long filledVersion = current(old).version();
            assertEquals(old.version() + 1, filledVersion);
            assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", old.orderId()));
            gateway.release.countDown();
            assertEquals(OrderStatus.FILLED, t1.get(15, TimeUnit.SECONDS).status());
            assertEquals(OrderStatus.FILLED, current(old).status());
            assertEquals(filledVersion, current(old).version());
            assertCas(old, "ACCEPTED", 0);
            assertEquals(0, eventCount(old, "OrderAck"));
            assertEquals(0, actionCount(old, "ORDER_ACKED"));
            assertEquals(0, count("SELECT count(*) FROM audit_logs WHERE actor_id=? AND action='ORDER_STATUS_TRANSITION' AND detail_json->>'to'='ACCEPTED'", old.orderId()));
            assertEquals(1, actionCount(old, "STALE_PROVIDER_RESULT_IGNORED"));
            assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", old.orderId()));
            assertEquals(2L, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", old.traceId()));
            System.out.println("C1 PLACE PASS: FILLED version=" + filledVersion + " Trade=1 Ledger=2 staleCAS=0 " + cas);
        } finally {
            gateway.release.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    /** R03：保持现有 reconcile 两步状态图，版本按两次真实迁移递增。 */
    @Test void staleCancelAckPreservesCommittedFilledThroughRealSpringTransactions() throws Exception {
        var order = place(request("stale-cancel"));
        gateway.pauseCancel = true;
        var executor = Executors.newSingleThreadExecutor();
        try {
            var t1 = executor.submit(() -> commands.cancelOrder(cancel(order)));
            assertTrue(gateway.reached.await(15, TimeUnit.SECONDS));
            var old = gateway.inFlight.get();
            assertEquals(OrderStatus.CANCEL_REQUESTED, current(order).status());
            gateway.venue.reportFilled(order.externalOrderId());
            assertEquals(1, reconcile.reconcileOnce(100));
            assertEquals(OrderStatus.FILLED, current(order).status());
            long filledVersion = current(order).version();
            assertEquals(old.version() + 2, filledVersion);
            gateway.release.countDown();
            assertEquals(OrderStatus.FILLED, t1.get(15, TimeUnit.SECONDS).status());
            assertEquals(OrderStatus.FILLED, current(order).status());
            assertEquals(filledVersion, current(order).version());
            assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", order.orderId()));
            assertEquals(2L, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", order.traceId()));
            assertCas(old, "CANCELLED", 0);
            assertEquals(0, eventCount(order, "CancelAck"));
            assertEquals(0, actionCount(order, "ORDER_CANCELLED"));
            assertEquals(0, count("SELECT count(*) FROM audit_logs WHERE actor_id=? AND action='ORDER_STATUS_TRANSITION' AND detail_json->>'to'='CANCELLED'", order.orderId()));
            assertEquals(1, actionCount(order, "STALE_PROVIDER_RESULT_IGNORED"));
            System.out.println("C1 CANCEL PASS: FILLED version=" + filledVersion + " Trade=1 Ledger=2 staleCAS=0 " + cas);
        } finally {
            gateway.release.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    /** 真实 command/reconcile/command 构成 ABA；旧拒绝不能借同名状态覆盖新撤单代际。 */
    @Test void staleCancelRejectCannotClaimNewCancelGenerationAfterAba() throws Exception {
        var order = place(request("aba"));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var oldResult = executor.submit(() -> commands.cancelOrder(new CancelOrderRequest(order.orderId(),
                    order.accountId(), order.clientOrderId(), "ABA_OLD", order.traceId())));
            assertTrue(gateway.oldReached.await(15, TimeUnit.SECONDS));
            var old = current(order);
            assertEquals(OrderStatus.CANCEL_REQUESTED, old.status());
            assertEquals(0, reconcile.reconcileOnce(100));
            assertEquals(OrderStatus.ACCEPTED, current(order).status());
            assertEquals(old.version() + 2, current(order).version());
            var newResult = executor.submit(() -> commands.cancelOrder(new CancelOrderRequest(order.orderId(),
                    order.accountId(), order.clientOrderId(), "ABA_NEW", order.traceId())));
            assertTrue(gateway.newReached.await(15, TimeUnit.SECONDS));
            var next = current(order);
            assertEquals(old.status(), next.status());
            assertEquals(old.version() + 3, next.version());
            long transitions = actionCount(order, "ORDER_STATUS_TRANSITION");
            gateway.oldRelease.countDown();
            assertEquals(OrderStatus.CANCEL_REQUESTED, oldResult.get(15, TimeUnit.SECONDS).status());
            assertEquals(next, current(order));
            assertCas(old, "CANCEL_REJECTED", 0);
            assertEquals(transitions, actionCount(order, "ORDER_STATUS_TRANSITION"));
            assertEquals(0, actionCount(order, "ORDER_CANCEL_REJECTED"));
            assertEquals(0, eventCount(order, "CancelReject"));
            assertEquals(1, actionCount(order, "STALE_PROVIDER_RESULT_IGNORED"));
            gateway.newRelease.countDown();
            assertEquals(OrderStatus.CANCELLED, newResult.get(15, TimeUnit.SECONDS).status());
            assertEquals(next.version() + 1, current(order).version());
            assertCas(next, "CANCELLED", 1);
            assertNoFillPersistence(order);
            System.out.println("C1 ABA PASS: old=" + old.version() + " new=" + next.version() + " staleCAS=0 " + cas);
        } finally {
            gateway.oldRelease.countDown(); gateway.newRelease.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    /** resolve 后在准备事务中暂停，较新 FILLED 提交后必须在 gateway 前失败。 */
    @Test void stalePreCancelSnapshotNeverDispatchesGateway() throws Exception {
        var order = place(request("prepare-race"));
        CountDownLatch reached = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> {
            reached.countDown();
            assertTrue(release.await(15, TimeUnit.SECONDS));
            return call.callRealMethod();
        }).when(writes).prepareCancelOrder(any(), any());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> commands.cancelOrder(cancel(order)));
            assertTrue(reached.await(15, TimeUnit.SECONDS));
            gateway.venue.reportFilled(order.externalOrderId());
            assertEquals(1, reconcile.reconcileOnce(100));
            var filled = current(order);
            release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(15, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals(filled, current(order));
            assertEquals(OrderStatus.FILLED, filled.status());
            assertEquals(0, gateway.cancelCalls.get());
            assertCas(order, "CANCEL_REQUESTED", 0);
            assertEquals(0, eventCount(order, "CancelAck"));
            assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", order.orderId()));
            assertEquals(2L, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", order.traceId()));
            System.out.println("C1 PRE-CANCEL PASS: FILLED version=" + filled.version() + " outbound=0 " + cas);
        } finally {
            release.countDown(); executor.shutdownNow();
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
    private long eventCount(OrderRecord order, String type) {
        return count("SELECT count(*) FROM event_store WHERE trace_id=? AND event_type=?", order.traceId(), type);
    }
    private long actionCount(OrderRecord order, String action) {
        return count("SELECT count(*) FROM audit_logs WHERE actor_id=? AND action=?", order.orderId(), action);
    }
    private void assertCas(OrderRecord expected, String target, int affected) {
        assertEquals(1, cas.stream().filter(c -> c.orderId().equals(expected.orderId())
                && c.expectedStatus().equals(expected.status().name()) && c.version() == expected.version()
                && c.target().equals(target) && c.affected() == affected && c.transactionActive()).count());
    }
    private record CasObservation(String orderId, String expectedStatus, long version, String target,
            int affected, boolean transactionActive) { }
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
        volatile CountDownLatch oldReached, oldRelease, newReached, newRelease;
        final java.util.concurrent.atomic.AtomicInteger cancelCalls = new java.util.concurrent.atomic.AtomicInteger();
        final AtomicReference<OrderRecord> inFlight = new AtomicReference<>();
        ControlledGateway(TradingChainPostgresIntegrationTest.DeterministicFakeVenue venue) { this.venue = venue; reset(); }
        void reset() {
            venue.reset(); pausePlace = false; pauseCancel = false;
            reached = new CountDownLatch(1); release = new CountDownLatch(1); inFlight.set(null);
            oldReached = new CountDownLatch(1); oldRelease = new CountDownLatch(1);
            newReached = new CountDownLatch(1); newRelease = new CountDownLatch(1); cancelCalls.set(0);
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
            cancelCalls.incrementAndGet();
            if (request.reason().startsWith("ABA_")) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                boolean old = request.reason().equals("ABA_OLD");
                (old ? oldReached : newReached).countDown();
                try { assertTrue((old ? oldRelease : newRelease).await(15, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                return new TradingCancelGatewayResult(!old, old ? TradingGatewayResultCategory.FATAL_FAILURE
                        : TradingGatewayResultCategory.ACCEPTED, null, Instant.now(), "SIM");
            }
            if (pauseCancel) barrier(order);
            return new TradingCancelGatewayResult(true, TradingGatewayResultCategory.ACCEPTED, null, Instant.now(), "SIM");
        }
        public TradingOrderStatusSnapshot getOrderStatus(OrderRecord order, String trace) { return venue.getOrderStatus(order, trace); }
    }
}
