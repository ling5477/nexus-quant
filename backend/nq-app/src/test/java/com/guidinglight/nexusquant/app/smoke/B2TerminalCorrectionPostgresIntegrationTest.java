package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.application.OrderCommandWriteService;
import com.guidinglight.nexusquant.trading.application.OrderLifecycleService;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 独立 PG 集成 fixture 可 seed SQL；真实进程 suite 的 Controller 仍严格只读。 */
@SpringBootTest(classes = {NexusQuantApplication.class, L4PlanBlockerPostgresIntegrationTest.Configuration.class}, properties = {
        "spring.main.allow-bean-definition-overriding=true", "spring.task.scheduling.enabled=false",
        "nq.runtime.trading-components.enabled=true", "nq.validation-operations.scheduler.enabled=false",
        "nq.okx.recovery.enabled=false", "nq.okx.ws.enabled=false", "nq.binance.ws.enabled=false",
        "nq.instrument.catalog-sync.enabled=false", "nq.env-safety.live-enabled=false",
        "nq.env-safety.ai-enabled=false", "nq.env-safety.dh-runtime-enabled=false",
        "nq.env-safety.real-provider-enabled=false", "nq.env-safety.real-client-enabled=false",
        "nq.env-safety.real-exchange-enabled=false", "nq.env-safety.no-outbound=true"
})
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "nq.b2.pg", matches = "true")
@ContextConfiguration(initializers = TradingChainPostgresIntegrationTest.NoExchangeOutboundInitializer.class)
class B2TerminalCorrectionPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderCommandWriteService writes;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired TradeRepository trades;
    @Autowired com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService reconcile;
    @MockitoSpyBean com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter adapter;
    @MockitoSpyBean OrderRepository orders;
    @MockitoSpyBean EventPublisherPort events;
    private OrderRecord order;
    private String url;

    @BeforeEach void fixture(TestInfo info) {
        url = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData().getURL());
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.endsWith("/nq_l4_blocker"));
        assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("16."));
        String id = UUID.randomUUID().toString();
        Long account = jdbc.queryForObject("INSERT INTO accounts(account_code,venue,status) VALUES (?,'OKX','ACTIVE') RETURNING account_id", Long.class, "b2-" + id);
        order = new OrderRecord("b2-" + id, account, null, "OKX", "BTC-USDT", "b2" + id.replace("-", "").substring(0, 28),
                "BUY", "LIMIT", new BigDecimal("100"), new BigDecimal("10"), "ext-" + id,
                OrderStatus.CANCELLED, "B2_FIXTURE", "b2-" + id, info.getTags().contains("live-environment") ? "LIVE" : "SIM");
        orders.insert(order, Instant.now());
        order = orders.findByOrderId(order.orderId()).orElseThrow();
    }

    @AfterEach void retireFixture() {
        if (order != null) jdbc.update("UPDATE orders SET status='REJECTED',reason='B2_FIXTURE_CLEANUP' WHERE order_id=?", order.orderId());
    }
    @AfterAll static void restoreProxy() { ExchangeNoOutboundGuard.restoreDefault(); }

    @Test void partialThenThreeDurableFillsCorrectOnceAndOrdinaryLifecycleCannot() {
        fill("4", "0");
        assertEquals(order, lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId()));
        assertThrows(IllegalStateException.class, () -> lifecycle.markFilled(order.orderId(), "ordinary", order.traceId()));
        fill("3", "0.01"); fill("3", "0.02");
        assertEquals(0, new BigDecimal("10").compareTo(orders.durableExecutedQuantity(order.orderId())));
        OrderRecord corrected = lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId());
        assertEquals(OrderStatus.FILLED, corrected.status()); assertEquals(1, corrected.version());
        assertEquals(corrected, lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId()));
        assertCorrectionCounts(1);
        assertEquals(order.externalOrderId(), corrected.externalOrderId());
    }

    @Test @Tag("live-environment") void livePartialThenFullInheritsCanonicalEnvironment() {
        partialThenThreeDurableFillsCorrectOnceAndOrdinaryLifecycleCannot();
        assertEquals("LIVE", jdbc.queryForObject("SELECT DISTINCT trade_env FROM trades WHERE order_id=?", String.class, order.orderId()));
    }

    @Test @Tag("live-environment") void liveOverfillFailsClosed() {
        overfillRejectsServiceAndAtomicRepositoryGuard();
    }

    @Test void simOrderRejectsExistingLiveTradeBeforeLedgerReplay() {
        rejectMismatchedEnvironment("LIVE");
    }

    @Test @Tag("live-environment") void liveOrderRejectsExistingSimTradeBeforeLedgerReplay() {
        rejectMismatchedEnvironment("SIM");
    }

    private void rejectMismatchedEnvironment(String wrongEnvironment) {
        fill("10", "0.01");
        assertEquals(order.tradeEnv(), jdbc.queryForObject("SELECT trade_env FROM trades WHERE order_id=?", String.class, order.orderId()));
        // 仅损坏隔离 PG 夹具，证明历史误绑定不会被自动改写或跨环境累计。
        jdbc.update("UPDATE trades SET trade_env=? WHERE order_id=?", wrongEnvironment, order.orderId());
        String fillId = jdbc.queryForObject("SELECT exchange_trade_id FROM trades WHERE order_id=?", String.class, order.orderId());
        assertThrows(IllegalStateException.class, () -> trades.findByOrderId(order.orderId()));
        assertThrows(IllegalStateException.class, () -> trades.findByExchangeAndExchangeTradeId("OKX", fillId));
        assertThrows(IllegalStateException.class, () -> trades.findAllByOrderId(order.orderId(), 100));
        assertThrows(IllegalStateException.class, () -> lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId()));
        assertEquals(0, orders.compareAndSetCancelledToFilled(order.orderId(), 0, "proof", Instant.now()));
        doReturn(java.util.List.of()).when(adapter).listTradeReports(order.symbol(), order.externalOrderId(), order.traceId());
        assertEquals("TRADE_ORDER_ENVIRONMENT_MISMATCH", assertThrows(IllegalStateException.class,
                () -> reconcile.reconcileOnce(100)).getMessage());
        assertEquals(order, orders.findByOrderId(order.orderId()).orElseThrow());
        assertEquals(wrongEnvironment, jdbc.queryForObject("SELECT trade_env FROM trades WHERE order_id=?", String.class, order.orderId()));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE trace_id=?", Long.class, order.traceId()));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id=? AND action='OKX_LEDGER_RECOVERY_INCOMPLETE' AND detail_json->>'reason'='TRADE_ORDER_ENVIRONMENT_MISMATCH'", Long.class, order.orderId()));
        assertCorrectionCounts(0);
    }

    @Test void overfillRejectsServiceAndAtomicRepositoryGuard() {
        fill("10", "0");
        // 故意损坏专用 fixture，证明历史/旁路 overfill 同样不能触发终态纠正。
        jdbc.update("UPDATE trades SET qty=10.00000001 WHERE order_id=?", order.orderId());
        assertTrue(assertThrows(IllegalStateException.class,
                () -> lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId())).getMessage().contains("OVERFILL"));
        assertEquals(0, orders.compareAndSetCancelledToFilled(order.orderId(), 0, "proof", Instant.now()));
        assertEquals(order, orders.findByOrderId(order.orderId()).orElseThrow()); assertCorrectionCounts(0);
    }

    @Test void foreignFillIdentityCannotProveFullExecution() {
        fill("10", "0");
        jdbc.update("UPDATE trades SET external_order_id='foreign' WHERE order_id=?", order.orderId());
        assertThrows(IllegalStateException.class, () -> orders.durableExecutedQuantity(order.orderId()));
        assertEquals(0, orders.compareAndSetCancelledToFilled(order.orderId(), 0, "proof", Instant.now()));
        assertEquals(order, orders.findByOrderId(order.orderId()).orElseThrow());
    }

    @Test void sqlRechecksFullProofAfterConcurrentDurableFillCommit() {
        fill("10", "0");
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            if (once.compareAndSet(false, true)) {
                // 独立连接提交，不能被待测 Spring 事务的失败一起回滚。
                try (var c = java.sql.DriverManager.getConnection(url, "postgres", "");
                     var s = c.prepareStatement("INSERT INTO trades(trade_id,order_id,account_id,symbol,exchange,external_order_id,exchange_trade_id,price,qty,fee,fee_currency,trace_id,ts) VALUES (?,?,?,'BTC-USDT','OKX',?,?,100,0.01,0,'USDT',?,CURRENT_TIMESTAMP)")) {
                    String id = UUID.randomUUID().toString();
                    s.setString(1, id); s.setString(2, order.orderId()); s.setLong(3, order.accountId());
                    s.setString(4, order.externalOrderId()); s.setString(5, id); s.setString(6, order.traceId()); s.executeUpdate();
                }
            }
            return result;
        }).when(orders).durableExecutedQuantity(order.orderId());
        assertTrue(assertThrows(IllegalStateException.class,
                () -> lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId())).getMessage().contains("OVERFILL"));
        assertEquals(order, orders.findByOrderId(order.orderId()).orElseThrow()); assertCorrectionCounts(0);
    }

    @RepeatedTest(3) void concurrentCorrectionsIncrementVersionAndPublishExactlyOnce() throws Exception {
        fill("4", "0"); fill("3", "0.01"); fill("3", "0.02");
        var reached = new CountDownLatch(2);
        doAnswer(call -> {
            reached.countDown(); assertTrue(reached.await(10, TimeUnit.SECONDS)); return call.callRealMethod();
        }).when(orders).compareAndSetCancelledToFilled(eq(order.orderId()), eq(0L), anyString(), any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId()));
            var second = executor.submit(() -> lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId()));
            assertEquals(1, first.get(15, TimeUnit.SECONDS).version());
            assertEquals(1, second.get(15, TimeUnit.SECONDS).version());
        }
        assertEquals(OrderStatus.FILLED, orders.findByOrderId(order.orderId()).orElseThrow().status());
        assertCorrectionCounts(1);
    }

    @Test void failedEventAppendRollsBackStatusVersionAndAudit() {
        fill("10", "0");
        doThrow(new IllegalStateException("B2_EVENT_STORAGE_FAILURE")).when(events).append(eq("order.event.v1"), any());
        assertThrows(IllegalStateException.class, () -> lifecycle.reconcileCancelledExecution(order.orderId(), order.traceId()));
        assertEquals(order, orders.findByOrderId(order.orderId()).orElseThrow()); assertCorrectionCounts(0);
    }

    @Test void concurrentDifferentFillsCannotOverfillBetweenPreflightAndInsert() throws Exception {
        fill("4", "0");
        var start = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> insert = () -> {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            try { fill("4", "0.01"); return true; }
            catch (IllegalStateException ex) { assertTrue(ex.getMessage().contains("OVERFILL")); return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(insert); var second = executor.submit(insert); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(0, new BigDecimal("8").compareTo(orders.durableExecutedQuantity(order.orderId())));
        assertEquals(order, orders.findByOrderId(order.orderId()).orElseThrow());
    }

    private void fill(String qty, String fee) {
        String id = UUID.randomUUID().toString();
        trades.insert(new PaperTradeRecord("trd-" + id, order.orderId(), order.accountId(), order.symbol(), order.venue(),
                order.externalOrderId(), id, order.price(), new BigDecimal(qty), new BigDecimal(fee), "USDT", order.traceId(), Instant.now()));
    }
    private void assertCorrectionCounts(long expected) {
        assertEquals(expected, jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id=? AND action='ORDER_TERMINAL_EXECUTION_CORRECTED'", Long.class, order.orderId()));
        assertEquals(expected, jdbc.queryForObject("SELECT count(*) FROM event_store WHERE trace_id=? AND payload_json->'payload'->>'reason'='RECONCILE_FULL_EXECUTION_PROVEN'", Long.class, order.traceId()));
    }
}
