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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * C1 使用真实事务与确定性 barrier 验证 versioned OCC；C2 验证撤单终态的成交与账本回补。
 * 测试只运行于任务专用临时 PostgreSQL；不代表 L4 qualification。
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
    @MockitoSpyBean com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter adapter;
    @MockitoSpyBean com.guidinglight.nexusquant.scheduler.service.LedgerModuleTradeLedgerGateway ledgerGateway;
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
        jdbc.update("DELETE FROM reconciliation_scan_cursors WHERE venue='OKX'");
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
            // 仅在断言完成后退役临时 fixture；下一用例会清空 venue，不能留下可扫描的 CANCELLED。
            jdbc.update("UPDATE orders SET status='REJECTED',reason='L4_TEST_CLEANUP' WHERE order_id=?", id);
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

    /** R04 / C2：撤单后到达的成交只回补事实，订单终态、version 和状态事件均不改变。 */
    @Test void cancelledOrderWithVenueFillBackfillsTradeAndLedgerIdempotently() {
        var cancelled = place(request("blind-spot"));
        commands.cancelOrder(cancel(cancelled));
        var before = current(cancelled);
        var factsBefore = orderFacts(cancelled);
        int orderQueries = gateway.venue.orderQueryCount();
        gateway.venue.reportFilled(cancelled.externalOrderId());
        int queries = gateway.venue.fillQueryCount();
        assertEquals(1, reconcile.reconcileOnce(100));
        assertEquals(queries + 1, gateway.venue.fillQueryCount());
        assertCancelledFacts(before, factsBefore, 1, 2, 1);
        assertTerminalAudit(cancelled, 1);
        System.out.println("C2 FIRST PASS: CANCELLED version=" + before.version()
                + " fillQueriesDelta=1 newTrades=1 Trade=1 Ledger=2 TradeExecuted=1 falseStatusEvents=0");
        assertEquals(0, reconcile.reconcileOnce(100));
        assertEquals(queries + 2, gateway.venue.fillQueryCount());
        assertCancelledFacts(before, factsBefore, 1, 2, 1);
        assertTerminalAudit(cancelled, 0);
        verify(adapter, times(2)).listTradeReports(cancelled.symbol(), cancelled.externalOrderId(), cancelled.traceId());
        assertEquals(orderQueries, gateway.venue.orderQueryCount());
        assertEquals(1, gateway.cancelCalls.get());
        assertEquals(1, gateway.venue.placeCount());
        assertEquals(0, actionCount(cancelled, "OKX_FILLED_ORDER_FILL_BACKFILL_COMPLETED"));
        assertEquals(1, eventCount(cancelled, "LedgerPosted"));
        System.out.println("C2 SECOND PASS: newTrades=0 Trade=1 Ledger=2 duplicateTrade=0 duplicateEvent=0 versionUnchanged=true");
        var control = place(request("scan-control"));
        gateway.venue.reportFilled(control.externalOrderId());
        assertEquals(1, reconcile.reconcileOnce(100));
        assertEquals(OrderStatus.FILLED, current(control).status());
        assertEquals(1L, count("SELECT count(*) FROM trades WHERE order_id=?", control.orderId()));
        assertEquals(2L, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", control.traceId()));
        assertCancelledFacts(before, factsBefore, 1, 2, 1);
        System.out.println("C2 ACCEPTED positive control -> FILLED/Trade1/Ledger2");
    }

    /** 非零部分成交在 kill engaged 时仍恢复，第二轮不重复产生交易或账本事实。 */
    @Test void cancelledPartialFillRecoversWithKillEngaged() {
        var original = request("partial-cancel");
        var order = place(new PlaceOrderRequest(original.requestId(), original.accountId(), original.strategyRunId(),
                original.venue(), original.symbol(), original.clientOrderId(), original.idempotencyKey(), original.source(),
                original.side(), original.type(), original.price(), new BigDecimal("0.2"),
                original.timeInForce(), original.traceId()));
        commands.cancelOrder(cancel(order));
        var before = current(order);
        var facts = orderFacts(order);
        gateway.venue.reportFilled(order.externalOrderId());
        kill.engage(kill.snapshot().version(), "L4_TEST_ENGAGE", "l4-test", order.traceId());
        assertEquals(1, reconcile.reconcileOnce(100));
        BigDecimal executed = jdbc.queryForObject("SELECT qty FROM trades WHERE order_id=?", BigDecimal.class, order.orderId());
        assertTrue(executed.signum() > 0 && executed.compareTo(before.qty()) < 0);
        assertEquals(0, reconcile.reconcileOnce(100));
        assertCancelledFacts(before, facts, 1, 2, 1);
        assertEquals(KillSwitchStatus.ENGAGED, kill.snapshot().status());
        assertEquals(1, gateway.venue.placeCount());
        assertEquals(1, gateway.cancelCalls.get());
        System.out.println("C2 PARTIAL PASS: ordered=" + before.qty() + " executed=" + executed
                + " CANCELLED/Trade1/Ledger2 kill=ENGAGED");
    }

    /** 真实 Trade 已提交而账本失败后，沿用普通 ledger gateway 重放，不重复成交或事件。 */
    @Test void cancelledDurableTradeRecoversMissingLedgerWithoutNewVenueFill() {
        var order = place(request("cancel-ledger-recovery"));
        commands.cancelOrder(cancel(order));
        var before = current(order);
        var facts = orderFacts(order);
        gateway.venue.reportFilled(order.externalOrderId());
        doThrow(new IllegalStateException("C2_SYNTHETIC_LEDGER_FAILURE")).doCallRealMethod()
                .when(ledgerGateway).postTrade(argThat(r -> r.orderId().equals(order.orderId())));
        assertThrows(IllegalStateException.class, () -> reconcile.reconcileOnce(100));
        assertCancelledFacts(before, facts, 1, 0, 1);
        assertEquals(1, actionCount(order, "OKX_LEDGER_POST_FAILED"));
        String tradeId = jdbc.queryForObject("SELECT trade_id FROM trades WHERE order_id=?", String.class, order.orderId());
        // 交易所不再返回成交时，仍须用 durable Trade 完成账本重放。
        doReturn(List.of()).when(adapter).listTradeReports(order.symbol(), order.externalOrderId(), order.traceId());
        assertEquals(0, reconcile.reconcileOnce(100));
        assertCancelledFacts(before, facts, 1, 2, 1);
        assertEquals(1, actionCount(order, "OKX_LEDGER_RECOVERY_COMPLETED"));
        assertEquals(0, reconcile.reconcileOnce(100));
        assertCancelledFacts(before, facts, 1, 2, 1);
        assertEquals(tradeId, jdbc.queryForObject("SELECT trade_id FROM trades WHERE order_id=?", String.class, order.orderId()));
        assertEquals(1, eventCount(order, "LedgerPosted"));
        System.out.println("C2 DURABLE RECOVERY PASS: Trade1/Ledger0 -> Trade1/Ledger2 -> Trade1/Ledger2 TradeExecuted=1");
    }

    @Test void cancelledWithoutVenueFillHasNoExecutionFacts() {
        var order = place(request("cancel-no-fill"));
        commands.cancelOrder(cancel(order));
        var before = current(order);
        var facts = orderFacts(order);
        assertEquals(0, reconcile.reconcileOnce(100));
        assertCancelledFacts(before, facts, 0, 0, 0);
        assertTerminalAudit(order, 0);
        assertEquals(1, gateway.venue.fillQueryCount());
        assertEquals(0, gateway.venue.orderQueryCount());
        assertEquals(1, gateway.cancelCalls.get());
        System.out.println("C2 NO FILL PASS: CANCELLED Trade=0 Ledger=0 TradeExecuted=0");
    }

    /** 两类候选同时有成交时，总预算为一只能处理较早的一个；不承诺跨轮公平性。 */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sharedLimitOneProcessesOnlyOneOrder(boolean cancelledFirst) {
        var terminal = place(request("bounded-terminal"));
        commands.cancelOrder(cancel(terminal));
        var terminalBefore = current(terminal);
        var terminalFacts = orderFacts(terminal);
        var active = place(request("bounded-active"));
        var first = cancelledFirst ? terminal : active;
        var second = cancelledFirst ? active : terminal;
        // 只固定专用数据库中本例订单的排序，不使用 sleep 或更改生产排序。
        jdbc.update("UPDATE orders SET created_at=TIMESTAMPTZ '2020-01-01 00:00:00+00' WHERE order_id=?", first.orderId());
        jdbc.update("UPDATE orders SET created_at=TIMESTAMPTZ '2020-01-02 00:00:00+00' WHERE order_id=?", second.orderId());
        gateway.venue.reportFilled(terminal.externalOrderId());
        gateway.venue.reportFilled(active.externalOrderId());
        kill.engage(kill.snapshot().version(), "L4_TEST_ENGAGE", "l4-test", first.traceId());
        assertEquals(1, reconcile.reconcileOnce(1));
        assertEquals(1, gateway.venue.fillQueryCount());
        verify(adapter).listTradeReports(first.symbol(), first.externalOrderId(), first.traceId());
        verify(adapter, never()).listTradeReports(second.symbol(), second.externalOrderId(), second.traceId());
        assertEquals(1, count("SELECT count(*) FROM trades WHERE order_id=?", first.orderId()));
        assertEquals(2, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", first.traceId()));
        assertNoFillPersistence(second);
        assertCancelledFacts(terminalBefore, terminalFacts, cancelledFirst ? 1 : 0, cancelledFirst ? 2 : 0,
                cancelledFirst ? 1 : 0);
        assertEquals(cancelledFirst ? OrderStatus.ACCEPTED : OrderStatus.FILLED, current(active).status());
        assertEquals(KillSwitchStatus.ENGAGED, kill.snapshot().status());
        assertEquals(2, gateway.venue.placeCount());
        assertEquals(1, gateway.cancelCalls.get());
        System.out.println("C2 TOTAL BUDGET PASS: limit=1 candidates=1 selected=" + first.orderId()
                + " selectedStatus=" + (cancelledFirst ? "CANCELLED" : "ACCEPTED")
                + " selectedCreatedAt=2020-01-01 other=" + second.orderId()
                + " otherCreatedAt=2020-01-02 fillQueries=1 Trade=1 Ledger=2 otherTrade=0 otherLedger=0");
    }

    /** 零费用两条分录，非零费用增加成对费用分录；重放保持完整账本行不变。 */
    @ParameterizedTest
    @ValueSource(strings = {"0", "0.02"})
    void cancelledFeeFactsConvergeAndReplayWithoutDuplicateLedger(String feeText) {
        var order = place(request("cancel-fee"));
        commands.cancelOrder(cancel(order));
        var before = current(order);
        var facts = orderFacts(order);
        BigDecimal fee = new BigDecimal(feeText);
        int entries = fee.signum() == 0 ? 2 : 4;
        var report = new com.guidinglight.nexusquant.adapter.api.model.AdapterTradeReport(
                "OKX", order.accountId(), order.symbol(), order.clientOrderId(), order.externalOrderId(),
                "fee-" + order.orderId(), order.side(), order.price(), order.qty(), fee, "USDT", Instant.EPOCH,
                "synthetic", order.traceId(), "SIM");
        doReturn(List.of(report)).when(adapter).listTradeReports(order.symbol(), order.externalOrderId(), order.traceId());
        assertEquals(1, reconcile.reconcileOnce(1));
        assertCancelledFacts(before, facts, 1, entries, 1);
        String tradeId = jdbc.queryForObject("SELECT trade_id FROM trades WHERE order_id=?", String.class, order.orderId());
        assertEquals(0, fee.compareTo(jdbc.queryForObject("SELECT fee FROM trades WHERE order_id=?", BigDecimal.class, order.orderId())));
        var ledgerBefore = jdbc.queryForList("SELECT * FROM ledger_entries WHERE trace_id=? ORDER BY idempotency_key", order.traceId());
        assertEquals(0, new BigDecimal("-10").compareTo(jdbc.queryForObject(
                "SELECT delta FROM ledger_entries WHERE idempotency_key=?", BigDecimal.class, tradeId + ":LEDGER:1")));
        assertEquals(0, new BigDecimal("10").compareTo(jdbc.queryForObject(
                "SELECT delta FROM ledger_entries WHERE idempotency_key=?", BigDecimal.class, tradeId + ":LEDGER:2")));
        assertEquals(0, jdbc.queryForObject("SELECT SUM(delta) FROM ledger_entries WHERE trace_id=?", BigDecimal.class, order.traceId()).signum());
        assertEquals(fee.signum() == 0 ? 0 : 2, count("SELECT count(*) FROM ledger_entries WHERE trace_id=? AND idempotency_key LIKE '%:FEE_%'", order.traceId()));
        if (fee.signum() > 0) {
            assertEquals(0, fee.negate().compareTo(jdbc.queryForObject(
                    "SELECT delta FROM ledger_entries WHERE idempotency_key=?", BigDecimal.class, tradeId + ":LEDGER:FEE_1")));
            assertEquals(0, fee.compareTo(jdbc.queryForObject(
                    "SELECT delta FROM ledger_entries WHERE idempotency_key=?", BigDecimal.class, tradeId + ":LEDGER:FEE_2")));
        }
        assertEquals(0, reconcile.reconcileOnce(1));
        assertCancelledFacts(before, facts, 1, entries, 1);
        assertEquals(ledgerBefore, jdbc.queryForList("SELECT * FROM ledger_entries WHERE trace_id=? ORDER BY idempotency_key", order.traceId()));
        // 隐藏 venue 回报后仍重放同一个 durable Trade，费用分录不得丢失或重复。
        doReturn(List.of()).when(adapter).listTradeReports(order.symbol(), order.externalOrderId(), order.traceId());
        assertEquals(0, reconcile.reconcileOnce(1));
        assertCancelledFacts(before, facts, 1, entries, 1);
        assertEquals(ledgerBefore, jdbc.queryForList("SELECT * FROM ledger_entries WHERE trace_id=? ORDER BY idempotency_key", order.traceId()));
        assertEquals(1, eventCount(order, "LedgerPosted"));
        assertEquals(1, gateway.venue.placeCount());
        assertEquals(1, gateway.cancelCalls.get());
        verify(adapter, never()).getOrder(any());
        verify(adapter, times(3)).listTradeReports(order.symbol(), order.externalOrderId(), order.traceId());
        System.out.println("C2 FEE PASS: fee=" + fee + " Order=CANCELLED version=" + before.version()
                + " Trade=1 Ledger=" + entries + " TradeExecuted=1 LedgerPosted=1 repeatedAndDurableReplay=UNCHANGED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel-empty", "cancel-converged", "filled-converged", "null-identity",
            "blank-identity", "multiple", "active-victim", "scheduled"})
    void terminalPrefixCannotStarveEligibleVictim(String scenario) {
        int prefixSize = scenario.equals("scheduled") ? 100 : scenario.equals("multiple") ? 2 : 1;
        int budget = prefixSize;
        var prefix = new java.util.ArrayList<OrderRecord>();
        for (int i = 0; i < prefixSize; i++) {
            var old = place(request("review-old-" + i));
            if (!scenario.equals("filled-converged")) commands.cancelOrder(cancel(old));
            jdbc.update("UPDATE orders SET created_at=TIMESTAMPTZ '2020-01-01' + (? * INTERVAL '1 second') WHERE order_id=?", i, old.orderId());
            if (scenario.endsWith("converged")) {
                gateway.venue.reportFilled(old.externalOrderId());
                assertEquals(1, reconcile.reconcileOnce(budget));
                assertEquals(1, count("SELECT count(*) FROM trades WHERE order_id=?", old.orderId()));
                assertEquals(2, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", old.traceId()));
            }
            // V5 双向同步身份列；缺失身份 fixture 必须同时清空两列，并验证数据库读回。
            if (scenario.equals("null-identity")) {
                assertEquals(1, jdbc.update("UPDATE orders SET external_order_id=NULL, exchange_order_id=NULL WHERE order_id=?", old.orderId()));
                assertNull(current(old).externalOrderId());
            }
            if (scenario.equals("blank-identity")) {
                assertEquals(1, jdbc.update("UPDATE orders SET external_order_id='   ', exchange_order_id='   ' WHERE order_id=?", old.orderId()));
                assertTrue(current(old).externalOrderId().isBlank());
            }
            prefix.add(current(old));
        }
        var victim = place(request("review-victim"));
        if (!scenario.equals("active-victim")) commands.cancelOrder(cancel(victim));
        gateway.venue.reportFilled(victim.externalOrderId());
        jdbc.update("UPDATE orders SET created_at=TIMESTAMPTZ '2020-01-02' WHERE order_id=?", victim.orderId());
        var before = current(victim);
        var states = List.of(OrderStatus.SENT, OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED,
                OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCEL_REJECTED, OrderStatus.FILLED, OrderStatus.CANCELLED);
        if (scenario.equals("active-victim")) {
            var oldStates = states.stream().filter(s -> s != OrderStatus.CANCELLED).toList();
            assertEquals(List.of(victim.orderId()), commands.findOrdersByStatuses(oldStates, budget).stream().map(OrderRecord::orderId).toList());
        }
        kill.engage(kill.snapshot().version(), "REVIEW_ONLY_FIXTURE", "review", victim.traceId());
        int placeBefore = gateway.venue.placeCount(), cancelBefore = gateway.cancelCalls.get();
        jdbc.update("DELETE FROM reconciliation_scan_cursors WHERE venue='OKX'");
        // 原八个反例保留相同稳定集合及预算；两轮必须到达末尾，后续三轮证明重放幂等。
        for (int oldRound = 0; oldRound < 5; oldRound++) {
            assertEquals(prefix.stream().map(OrderRecord::orderId).toList(),
                    commands.findOrdersByStatuses(states, budget).stream().map(OrderRecord::orderId).toList());
        }
        System.out.println("C2_ORIGINAL_SQL_REPRO scenario=" + scenario + " rounds=5 victimSelected=false");
        for (int round = 1; round <= 5; round++) {
            int queriesBefore = gateway.venue.fillQueryCount();
            if (scenario.equals("scheduled")) reconcile.scheduledReconcile();
            else reconcile.reconcileOnce(budget);
            assertTrue(gateway.venue.fillQueryCount() - queriesBefore <= budget);
            if (round == 1) assertNoFillPersistence(victim);
            else {
                assertEquals(1, count("SELECT count(*) FROM trades WHERE order_id=?", victim.orderId()));
                assertEquals(2, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", victim.traceId()));
            }
            for (var old : prefix) assertEquals(old, current(old));
            assertEquals(round, jdbc.queryForObject("SELECT revision FROM reconciliation_scan_cursors WHERE venue='OKX'", Long.class));
            System.out.println("C2_FAIR_SCAN scenario=" + scenario + " round=" + round + " budget=" + budget
                    + " victimTrades=" + count("SELECT count(*) FROM trades WHERE order_id=?", victim.orderId()));
        }
        if (scenario.endsWith("identity")) for (var old : prefix)
            verify(adapter, never()).listTradeReports(old.symbol(), old.externalOrderId(), old.traceId());
        assertEquals(0, reconcile.reconcileOnce(budget));
        assertEquals(1, count("SELECT count(*) FROM trades WHERE order_id=?", victim.orderId()));
        assertEquals(2, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", victim.traceId()));
        if (!scenario.equals("active-victim")) assertEquals(before, current(victim));
        else assertEquals(OrderStatus.FILLED, current(victim).status());
        assertEquals(placeBefore, gateway.venue.placeCount());
        assertEquals(cancelBefore, gateway.cancelCalls.get());
        assertEquals(KillSwitchStatus.ENGAGED, kill.snapshot().status());
        System.out.println("C2_FAIRNESS_PASS scenario=" + scenario + " sharedBudget=" + budget
                + " victimTrade=1 victimLedger=2 noAdditionalPlaceOrCancel=true");
    }


    @Test void reservationFailureDoesNotLoseCandidatesAndVenueQueryHoldsNoCursorLock() {
        var first = place(request("reservation-failure-a"));
        var second = place(request("reservation-failure-b"));
        commands.cancelOrder(cancel(first)); commands.cancelOrder(cancel(second));
        jdbc.update("UPDATE orders SET created_at=TIMESTAMPTZ '2020-01-01' WHERE order_id=?", first.orderId());
        jdbc.update("UPDATE orders SET created_at=TIMESTAMPTZ '2020-01-02' WHERE order_id=?", second.orderId());
        gateway.venue.reportFilled(first.externalOrderId()); gateway.venue.reportFilled(second.externalOrderId());
        var firstBefore = current(first); var secondBefore = current(second);
        kill.engage(kill.snapshot().version(), "C2_RESERVATION_TEST", "test", first.traceId());
        int places = gateway.venue.placeCount(), cancels = gateway.cancelCalls.get();
        doThrow(new IllegalStateException("synthetic read failure after reservation")).when(adapter)
                .listTradeReports(first.symbol(), first.externalOrderId(), first.traceId());
        assertThrows(IllegalStateException.class, () -> reconcile.reconcileOnce(1));
        assertEquals(1L, jdbc.queryForObject("SELECT revision FROM reconciliation_scan_cursors WHERE venue='OKX'", Long.class));
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            String url = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData().getURL());
            // venue 查询边界用另一个真实连接立即加锁；若预留锁尚未释放，NOWAIT 必须失败。
            try (var connection = java.sql.DriverManager.getConnection(url, "postgres", "");
                    var statement = connection.createStatement()) {
                statement.setQueryTimeout(2);
                try (var rows = statement.executeQuery("SELECT revision FROM reconciliation_scan_cursors WHERE venue='OKX' FOR UPDATE NOWAIT")) {
                    assertTrue(rows.next()); assertEquals(2, rows.getLong(1));
                }
            }
            return call.callRealMethod();
        }).when(adapter).listTradeReports(second.symbol(), second.externalOrderId(), second.traceId());
        assertEquals(1, reconcile.reconcileOnce(1));
        assertNoFillPersistence(first);
        doCallRealMethod().when(adapter).listTradeReports(first.symbol(), first.externalOrderId(), first.traceId());
        assertEquals(1, reconcile.reconcileOnce(1));
        assertEquals(firstBefore, current(first)); assertEquals(secondBefore, current(second));
        for (var order : List.of(first, second)) {
            assertEquals(1, count("SELECT count(*) FROM trades WHERE order_id=?", order.orderId()));
            assertEquals(2, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", order.traceId()));
        }
        assertEquals(places, gateway.venue.placeCount()); assertEquals(cancels, gateway.cancelCalls.get());
        assertEquals(KillSwitchStatus.ENGAGED, kill.snapshot().status());
        System.out.println("C2_NETWORK_BOUNDARY_PASS independentLock=AVAILABLE failedBatchRevisited=true");
    }

    private List<Long> orderFacts(OrderRecord order) {
        return List.of(actionCount(order, "ORDER_STATUS_TRANSITION"), actionCount(order, "ORDER_ACKED"),
                actionCount(order, "ORDER_CANCELLED"), actionCount(order, "ORDER_CANCEL_REQUESTED"),
                eventCount(order, "OrderAck"), eventCount(order, "CancelAck"),
                count("SELECT count(*) FROM event_store WHERE trace_id=? AND topic='order.event.v1'", order.traceId()));
    }

    private void assertCancelledFacts(OrderRecord before, List<Long> orderFactsBefore, long trades, long ledger, long events) {
        var after = current(before);
        assertEquals(OrderStatus.CANCELLED, after.status());
        assertEquals(before, after);
        assertEquals(before.version(), count("SELECT version FROM orders WHERE order_id=?", before.orderId()));
        assertEquals(orderFactsBefore, orderFacts(before));
        assertEquals(trades, count("SELECT count(*) FROM trades WHERE order_id=?", before.orderId()));
        assertEquals(ledger, count("SELECT count(*) FROM ledger_entries WHERE trace_id=?", before.traceId()));
        assertEquals(events, eventCount(before, "TradeExecuted"));
    }

    private void assertTerminalAudit(OrderRecord order, int newTrades) {
        assertEquals(1, count("SELECT count(*) FROM audit_logs WHERE actor_id=? "
                + "AND action='OKX_CANCELLED_ORDER_FILL_BACKFILL_COMPLETED' AND detail_json->>'status'='CANCELLED' "
                + "AND detail_json->>'external_order_id'=? AND detail_json->>'new_trades'=?",
                order.orderId(), order.externalOrderId(), String.valueOf(newTrades)));
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
