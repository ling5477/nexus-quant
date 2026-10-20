package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.AccountTradeEnvironmentAuthority;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.model.AdapterTradeReport;
import com.guidinglight.nexusquant.adapter.binance.trading.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.audit.infra.jdbc.JdbcAuditLogRepository;
import com.guidinglight.nexusquant.contracts.event.port.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.risk.application.port.RiskGate;
import com.guidinglight.nexusquant.scheduler.infra.jdbc.JdbcTradeRepository;
import com.guidinglight.nexusquant.scheduler.integration.LedgerModuleTradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.port.TradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.recovery.BinanceRecoveryService;
import com.guidinglight.nexusquant.scheduler.recovery.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.recovery.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.infra.JdbcTradingMaintenanceOperations;
import com.guidinglight.nexusquant.scheduler.scheduling.SchedulerTradingMaintenanceService;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations.Kind;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceService;
import com.guidinglight.nexusquant.trading.application.model.RecoveryReport;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.application.service.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.service.OrderCommandWriteService;
import com.guidinglight.nexusquant.trading.application.service.OrderLifecycleService;
import com.guidinglight.nexusquant.trading.application.service.RecoveryService;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrdinaryPlaceAuthorityRepository;
import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 新随机PG库验证迁移、持久领取及并发；受控worker只追加测试效果，不代表真实交易所扫描。 */
@EnabledIfSystemProperty(named = "nq.maintenance.pg.required", matches = "true")
@Timeout(120)
class TradingMaintenanceOperationPostgresIntegrationTest {
    @Test
    void originalReceiptSurvivesReconstructionAndPreventsDuplicateOrUnconfirmedExecution()
            throws Exception {
        String controlUrl = System.getenv("TASK_MAINTENANCE_DB_URL");
        String user = System.getenv("TASK_MAINTENANCE_DB_USER");
        String password = System.getenv("TASK_MAINTENANCE_DB_PASSWORD");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", controlUrl);
        assertNotNull(user);
        assertNotNull(password);
        String database = "maintenance_receipt_" + UUID.randomUUID().toString().replace("-", "");
        var control = new JdbcTemplate(new DriverManagerDataSource(controlUrl, user, password));
        control.setQueryTimeout(10);
        control.execute("CREATE DATABASE " + database);
        String url = controlUrl.substring(0, controlUrl.lastIndexOf('/') + 1) + database;
        var source =
                new DriverManagerDataSource(
                        url + "?connectTimeout=5&socketTimeout=15", user, password);
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        Path output = Path.of(System.getProperty("nq.maintenance.pg.evidence"));
        Files.createDirectories(output);
        boolean passed = false;
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var before =
                    Flyway.configure()
                            .dataSource(source)
                            .locations("classpath:db/migration")
                            .cleanDisabled(true)
                            .target("32")
                            .load();
            before.migrate();
            before.validate();
            jdbc.update(
                    "INSERT INTO users(id,username,password_hash) VALUES"
                        + " (1,'synthetic-maintenance-owner','!unused'),(2,'synthetic-maintenance-other','!unused')");
            String fingerprint =
                    jdbc.queryForObject(
                            "SELECT md5(string_agg(to_jsonb(x)::text,'' ORDER BY id)) FROM users x",
                            String.class);
            var latest =
                    Flyway.configure()
                            .dataSource(source)
                            .locations("classpath:db/migration")
                            .cleanDisabled(true)
                            .target("33")
                            .load();
            assertEquals(1, latest.migrate().migrationsExecuted);
            latest.validate();
            assertEquals(0, latest.migrate().migrationsExecuted);
            assertEquals(
                    fingerprint,
                    jdbc.queryForObject(
                            "SELECT md5(string_agg(to_jsonb(x)::text,'' ORDER BY id)) FROM users x",
                            String.class));
            jdbc.execute(
                    "CREATE TABLE maintenance_fixture_effects (id BIGSERIAL PRIMARY KEY, kind TEXT"
                            + " NOT NULL)");
            var worker = mock(TradingMaintenanceService.class);
            var calls = new AtomicInteger();
            when(worker.runReconcile("OKX", 100))
                    .thenAnswer(
                            invocation -> {
                                calls.incrementAndGet();
                                jdbc.update(
                                        "INSERT INTO maintenance_fixture_effects(kind) VALUES"
                                                + " ('RECONCILE')");
                                return 3;
                            });
            when(worker.runRecovery(eq("BINANCE"), anyString()))
                    .thenAnswer(
                            invocation -> {
                                calls.incrementAndGet();
                                jdbc.update(
                                        "INSERT INTO maintenance_fixture_effects(kind) VALUES"
                                                + " ('RECOVERY')");
                                return new RecoveryReport(
                                        Instant.EPOCH,
                                        Instant.EPOCH,
                                        2,
                                        4,
                                        1,
                                        0,
                                        invocation.getArgument(1));
                            });
            var manager = new DataSourceTransactionManager(source);
            var service = new JdbcTradingMaintenanceOperations(jdbc, worker, manager);
            UUID id = UUID.randomUUID();
            var completed =
                    service.submit(
                            id, 1, Kind.RECONCILE, "okx", 100, "synthetic-maintenance-first");
            assertEquals("COMPLETED", completed.state());
            assertTrue(completed.detail().contains("new_trades=3"));
            assertEquals(
                    completed,
                    service.submit(
                            id, 1, Kind.RECONCILE, "OKX", 100, "synthetic-maintenance-retry"));
            assertEquals(1, calls.get());
            assertThrows(
                    IllegalStateException.class,
                    () -> service.submit(id, 1, Kind.RECONCILE, "OKX", 101, "changed-input"));
            assertThrows(
                    IllegalStateException.class,
                    () -> service.submit(id, 2, Kind.RECONCILE, "OKX", 100, "other-owner"));
            assertTrue(service.find(id, 2).isEmpty());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            service.submit(
                                    UUID.randomUUID(),
                                    1,
                                    Kind.RECONCILE,
                                    "OKX",
                                    1001,
                                    "invalid-limit"));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            service.submit(
                                    UUID.randomUUID(),
                                    1,
                                    Kind.RECOVERY,
                                    "OKX",
                                    1,
                                    "invalid-recovery-limit"));
            var recovery =
                    service.submit(
                            UUID.randomUUID(),
                            1,
                            Kind.RECOVERY,
                            "BINANCE",
                            null,
                            "synthetic-recovery");
            assertEquals("COMPLETED", recovery.state());
            assertTrue(recovery.detail().contains("linked_external_order_ids=1"));
            assertFalse(recovery.detail().contains("invalid_transitions"));

            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            when(worker.runReconcile("OKX", 7))
                    .thenAnswer(
                            invocation -> {
                                calls.incrementAndGet();
                                entered.countDown();
                                assertTrue(release.await(8, TimeUnit.SECONDS));
                                jdbc.update(
                                        "INSERT INTO maintenance_fixture_effects(kind) VALUES"
                                                + " ('CONCURRENT')");
                                return 0;
                            });
            UUID concurrent = UUID.randomUUID();
            var execution =
                    threads.submit(
                            () ->
                                    service.submit(
                                            concurrent,
                                            1,
                                            Kind.RECONCILE,
                                            "OKX",
                                            7,
                                            "synthetic-concurrent"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                assertEquals(
                        "STARTED",
                        service.submit(concurrent, 1, Kind.RECONCILE, "OKX", 7, "concurrent-repeat")
                                .state());
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                service.submit(
                                        UUID.randomUUID(),
                                        2,
                                        Kind.RECOVERY,
                                        "BINANCE",
                                        null,
                                        "other-global-scan"));
                assertEquals(3, calls.get());
            } finally {
                release.countDown();
            }
            assertEquals("COMPLETED", execution.get(5, TimeUnit.SECONDS).state());
            var reconstructed =
                    new JdbcTradingMaintenanceOperations(
                            jdbc, worker, new DataSourceTransactionManager(source));
            assertEquals(completed, reconstructed.find(id, 1).orElseThrow());
            assertEquals(
                    completed,
                    reconstructed.submit(
                            id, 1, Kind.RECONCILE, "OKX", 100, "reconstructed-client"));
            assertEquals(3, calls.get());
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE trading_maintenance_operations SET venue='BINANCE'"
                                            + " WHERE operation_id=?",
                                    id));
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "DELETE FROM trading_maintenance_operations WHERE"
                                            + " operation_id=?",
                                    id));
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE trading_maintenance_operations SET"
                                            + " state='UNKNOWN',detail=NULL,completed_at=NULL WHERE"
                                            + " operation_id=?",
                                    id));

            verifyNonemptyCanonicalWorker(jdbc, manager, output);
            verifyCanonicalFillSettlement(source, jdbc, manager, output);

            UUID unknown = UUID.randomUUID();
            when(worker.runRecovery(eq("OKX"), anyString()))
                    .thenAnswer(
                            invocation -> {
                                calls.incrementAndGet();
                                jdbc.update(
                                        "INSERT INTO maintenance_fixture_effects(kind) VALUES"
                                                + " ('PARTIAL_BEFORE_LOSS')");
                                throw new IllegalStateException(
                                        "synthetic confidential text must not enter receipt");
                            });
            var pending =
                    service.submit(unknown, 1, Kind.RECOVERY, "OKX", null, "synthetic-unknown");
            assertEquals("UNKNOWN", pending.state());
            assertNull(pending.detail());
            assertNull(pending.completedAt());
            assertEquals(
                    pending,
                    reconstructed.submit(unknown, 1, Kind.RECOVERY, "OKX", null, "same-original"));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            reconstructed.submit(
                                    UUID.randomUUID(),
                                    1,
                                    Kind.RECONCILE,
                                    "BINANCE",
                                    10,
                                    "blind-repeat"));
            assertEquals(4, calls.get());
            assertEquals(
                    4,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM maintenance_fixture_effects", Integer.class));
            // 两笔只读候选与两笔成交候选是明确的隔离前提；金额及去重由组合证明核对。
            assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM trades", Integer.class));
            assertEquals(
                    10, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class));
            assertEquals(
                    0,
                    jdbc.queryForObject("SELECT count(*) FROM paper_trading_runs", Integer.class));
            Files.writeString(
                    output.resolve("result.json"),
                    new ObjectMapper()
                            .writeValueAsString(
                                    Map.of(
                                            "database",
                                            database,
                                            "flyway",
                                            33,
                                            "workerCalls",
                                            calls.get(),
                                            "concurrency",
                                            "one canonical worker invocation",
                                            "unknown",
                                            "retained, no retry",
                                            "restartScope",
                                            "service and connection reconstruction, not JVM"
                                                    + " restart",
                                            "controlledWorker",
                                            true,
                                            "realTradingScanClaimed",
                                            false)));
            passed = true;
        } finally {
            // 仅删除此测试自己新建且已通过的随机库；失败库保留供核查，绝不触碰工作候选库。
            if (passed) control.execute("DROP DATABASE " + database);
        }
    }

    /** 正式扫描器接真实仓储；只有交易所查询受控，局部失败不等于全部订单已对齐。 */
    private void verifyNonemptyCanonicalWorker(
            JdbcTemplate jdbc, DataSourceTransactionManager manager, Path output) throws Exception {
        long account =
                jdbc.queryForObject(
                        "INSERT INTO accounts(account_code,venue,status) VALUES"
                                + " ('synthetic-maintenance-scan','BINANCE','ACTIVE') RETURNING"
                                + " account_id",
                        Long.class);
        var orders = new JdbcOrderRepository(jdbc);
        for (String suffix : List.of("known", "unavailable")) {
            orders.insert(
                    new OrderRecord(
                            "synthetic-maintenance-" + suffix,
                            account,
                            null,
                            "BINANCE",
                            "BTC-USDT",
                            "synthetic-client-" + suffix,
                            "BUY",
                            "LIMIT",
                            BigDecimal.ONE,
                            BigDecimal.ONE,
                            "synthetic-external-" + suffix,
                            OrderStatus.ACCEPTED,
                            "SYNTHETIC_SCAN_ONLY",
                            "synthetic-scan",
                            "SIM"),
                    Instant.EPOCH);
        }
        String facts =
                jdbc.queryForObject(
                        "SELECT md5(string_agg(to_jsonb(x)::text,'' ORDER BY order_id)) FROM orders"
                                + " x",
                        String.class);
        var adapter = mock(BinanceExchangeAdapter.class);
        var queries = new AtomicInteger();
        when(adapter.getOrder(any()))
                .thenAnswer(
                        invocation -> {
                            queries.incrementAndGet();
                            var query = invocation.getArgument(0, AdapterOrderQuery.class);
                            if (query.clientOrderId().endsWith("unavailable"))
                                throw new IllegalStateException("SYNTHETIC_QUERY_UNAVAILABLE");
                            return new AdapterOrderSnapshot(
                                    account,
                                    "BINANCE",
                                    "BTC-USDT",
                                    query.clientOrderId(),
                                    query.externalOrderId(),
                                    "ACCEPTED",
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    "synthetic-query",
                                    query.traceId());
                        });
        var audit = new JdbcAuditLogRepository(jdbc, new ObjectMapper());
        var events = mock(EventPublisherPort.class);
        var writes = mock(OrderCommandWriteService.class);
        var gateway = mock(TradingVenueGateway.class);
        var ledger = mock(TradeLedgerGateway.class);
        var commands = new OrderCommandService(orders, audit, events, gateway, writes);
        var reconcile =
                new BinanceRestReconcileService(
                        commands,
                        new OrderLifecycleService(commands),
                        adapter,
                        new JdbcTradeRepository(jdbc),
                        ledger,
                        events,
                        audit);
        var recovery = new BinanceRecoveryService(commands, adapter, reconcile, audit, events);
        var workers =
                new SchedulerTradingMaintenanceService(
                        mock(OkxRestReconcileService.class),
                        reconcile,
                        recovery,
                        mock(RecoveryService.class));
        var operations = new JdbcTradingMaintenanceOperations(jdbc, workers, manager);
        UUID reconcileId = UUID.randomUUID(), recoveryId = UUID.randomUUID();
        var first =
                operations.submit(
                        reconcileId,
                        1,
                        Kind.RECONCILE,
                        "BINANCE",
                        100,
                        "synthetic-nonempty-reconcile");
        assertEquals("COMPLETED", first.state());
        assertTrue(first.detail().contains("new_trades=0"));
        assertEquals(2, queries.get());
        assertEquals(
                first,
                operations.submit(reconcileId, 1, Kind.RECONCILE, "BINANCE", 100, "same-original"));
        assertEquals(2, queries.get());
        var second =
                operations.submit(
                        recoveryId,
                        1,
                        Kind.RECOVERY,
                        "BINANCE",
                        null,
                        "synthetic-nonempty-recovery");
        assertEquals("COMPLETED", second.state());
        assertTrue(second.detail().contains("candidate_orders=2"));
        assertTrue(second.detail().contains("new_trades=0"));
        assertEquals(4, queries.get());
        assertEquals(
                second,
                operations.submit(recoveryId, 1, Kind.RECOVERY, "BINANCE", null, "same-original"));
        assertEquals(4, queries.get());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE"
                                + " action='BINANCE_RECONCILE_ORDER_FAILED'",
                        Integer.class));
        assertEquals(
                facts,
                jdbc.queryForObject(
                        "SELECT md5(string_agg(to_jsonb(x)::text,'' ORDER BY order_id)) FROM orders"
                                + " x",
                        String.class));
        verifyNoInteractions(writes, gateway, ledger);
        verify(adapter, times(2))
                .listTradeReports("BTC-USDT", "synthetic-external-known", "synthetic-scan");
        Files.writeString(
                output.resolve("canonical-worker-nonempty.json"),
                new ObjectMapper()
                        .writeValueAsString(
                                Map.of(
                                        "candidateOrders",
                                        2,
                                        "adapterQueries",
                                        4,
                                        "perOrderFailures",
                                        2,
                                        "newTrades",
                                        0,
                                        "originalReplayQueries",
                                        0,
                                        "ordersUnchanged",
                                        true,
                                        "controlledAdapter",
                                        true,
                                        "realProviderClaimed",
                                        false,
                                        "completedMeaning",
                                        "worker returned; not all orders aligned")));
    }

    /** 复用正式账务事务装配，证明扫描与恢复通过原状态机及Ledger落库，不模拟资金结果。 */
    private void verifyCanonicalFillSettlement(
            DriverManagerDataSource source,
            JdbcTemplate jdbc,
            DataSourceTransactionManager manager,
            Path output)
            throws Exception {
        try (var app = SignedFeeLedgerPostgresIntegrationTest.context(source)) {
            var orders = new JdbcOrderRepository(jdbc);
            var audit = new JdbcAuditLogRepository(jdbc, app.getBean(ObjectMapper.class));
            var events = app.getBean(EventStoreAppender.class);
            var risk = mock(RiskGate.class);
            var riskEvents = mock(RiskEventRepository.class);
            var authorities = mock(OrdinaryPlaceAuthorityRepository.class);
            var environment = mock(AccountTradeEnvironmentAuthority.class);
            var gateway = mock(TradingVenueGateway.class);
            app.registerBean(OrderRepository.class, () -> orders);
            app.registerBean(AuditLogRepository.class, () -> audit);
            app.registerBean(
                    OrderCommandWriteService.class,
                    () ->
                            new OrderCommandWriteService(
                                    orders,
                                    new InMemoryOrderStateMachine(),
                                    risk,
                                    audit,
                                    riskEvents,
                                    events,
                                    authorities,
                                    environment));
            var commands =
                    new OrderCommandService(
                            orders,
                            audit,
                            events,
                            gateway,
                            app.getBean(OrderCommandWriteService.class));
            var adapter = mock(BinanceExchangeAdapter.class);
            var queries = new AtomicInteger();
            long account =
                    jdbc.queryForObject(
                            "INSERT INTO accounts(account_code,venue,status) VALUES"
                                    + " ('synthetic-maintenance-funded','PAPER','ACTIVE') RETURNING"
                                    + " account_id",
                            Long.class);
            app.getBean(SimCashFundingPort.class)
                    .fundOnce(
                            account,
                            "synthetic-maintenance-funding",
                            new BigDecimal("1000"),
                            "synthetic-funding");
            when(adapter.getOrder(any()))
                    .thenAnswer(
                            invocation -> {
                                queries.incrementAndGet();
                                var query = invocation.getArgument(0, AdapterOrderQuery.class);
                                // 前一子场景的只读候选暂不可确认，不能用本场景数据替代其外部事实。
                                boolean own = query.accountId() == account;
                                return new AdapterOrderSnapshot(
                                        query.accountId(),
                                        "BINANCE",
                                        "BTC-USDT",
                                        query.clientOrderId(),
                                        query.externalOrderId(),
                                        own ? "FILLED" : "ACCEPTED",
                                        own
                                                ? AdapterResultCategory.SUCCESS
                                                : AdapterResultCategory.DEFERRED,
                                        null,
                                        new BigDecimal("100"),
                                        BigDecimal.ONE,
                                        own ? BigDecimal.ONE : BigDecimal.ZERO,
                                        new BigDecimal("100"),
                                        Instant.EPOCH,
                                        "controlled-maintenance-fact",
                                        query.traceId(),
                                        "SIM");
                            });
            when(adapter.listTradeReports(anyString(), anyString(), anyString()))
                    .thenAnswer(
                            invocation -> {
                                String external = invocation.getArgument(1);
                                return List.of(
                                        new AdapterTradeReport(
                                                "BINANCE",
                                                account,
                                                "BTC-USDT",
                                                "client-" + external,
                                                external,
                                                "fill-" + external,
                                                "BUY",
                                                new BigDecimal("100"),
                                                BigDecimal.ONE,
                                                new BigDecimal("0.1"),
                                                "USDT",
                                                Instant.EPOCH,
                                                "controlled-maintenance-fill",
                                                invocation.getArgument(2),
                                                "SIM"));
                            });
            var reconcile =
                    new BinanceRestReconcileService(
                            commands,
                            new OrderLifecycleService(commands),
                            adapter,
                            app.getBean(TradeRepository.class),
                            new LedgerModuleTradeLedgerGateway(app.getBean(TradeLedgerPort.class)),
                            events,
                            audit);
            var recovery = new BinanceRecoveryService(commands, adapter, reconcile, audit, events);
            var worker =
                    new SchedulerTradingMaintenanceService(
                            mock(OkxRestReconcileService.class),
                            reconcile,
                            recovery,
                            mock(RecoveryService.class));
            var operations = new JdbcTradingMaintenanceOperations(jdbc, worker, manager);
            String[] ids = {"synthetic-maintenance-fill-a", "synthetic-maintenance-fill-b"};
            UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
            var receipts = new ArrayList<TradingMaintenanceOperations.Receipt>();
            for (int index = 0; index < ids.length; index++) {
                String external = index == 0 ? "external-fill-a" : "external-fill-b";
                var order =
                        new OrderRecord(
                                ids[index],
                                account,
                                null,
                                "BINANCE",
                                "BTC-USDT",
                                "client-" + external,
                                "BUY",
                                "LIMIT",
                                new BigDecimal("100"),
                                BigDecimal.ONE,
                                index == 0 ? external : null,
                                OrderStatus.ACCEPTED,
                                "SYNTHETIC_EXISTING_VENUE_ORDER",
                                "synthetic-maintenance-fill",
                                "SIM");
                orders.insert(order, Instant.EPOCH);
                if (index == 1)
                    when(adapter.listOpenOrders(any()))
                            .thenReturn(
                                    List.of(
                                            new AdapterOrderSnapshot(
                                                    account,
                                                    "BINANCE",
                                                    "BTC-USDT",
                                                    order.clientOrderId(),
                                                    external,
                                                    "ACCEPTED",
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    "controlled-open-order",
                                                    "synthetic-maintenance-fill")));
                var kind = index == 0 ? Kind.RECONCILE : Kind.RECOVERY;
                UUID id = index == 0 ? firstId : secondId;
                Integer limit = index == 0 ? 100 : null;
                var receipt = operations.submit(id, 1, kind, "BINANCE", limit, "synthetic-fill");
                assertEquals("COMPLETED", receipt.state());
                assertTrue(receipt.detail().contains("new_trades=1"));
                if (index == 1)
                    assertTrue(receipt.detail().contains("linked_external_order_ids=1"));
                assertEquals(
                        OrderStatus.FILLED,
                        orders.findByOrderId(order.orderId()).orElseThrow().status());
                assertEquals(
                        external,
                        orders.findByOrderId(order.orderId()).orElseThrow().externalOrderId());
                int beforeReplay = queries.get();
                assertEquals(
                        receipt, operations.submit(id, 1, kind, "BINANCE", limit, "same-original"));
                assertEquals(beforeReplay, queries.get());
                receipts.add(receipt);
            }
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM trades WHERE account_id=?",
                            Integer.class,
                            account));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM (SELECT ref_id,currency FROM ledger_entries WHERE"
                                + " account_id=? GROUP BY ref_id,currency HAVING sum(delta)<>0) x",
                            Integer.class,
                            account));
            assertEquals(
                    0,
                    new BigDecimal("799.8")
                            .compareTo(
                                    app.getBean(JdbcLedgerPostingRepository.class)
                                            .currentBalance(account, "USDT", "SIM")));
            assertEquals(
                    0,
                    new BigDecimal("2")
                            .compareTo(
                                    jdbc.queryForObject(
                                            "SELECT qty FROM positions WHERE account_id=? AND"
                                                    + " symbol='BTC-USDT'",
                                            BigDecimal.class,
                                            account)));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM orders WHERE trade_env='LIVE'", Integer.class));
            verifyNoInteractions(gateway, risk, riskEvents, authorities, environment);
            Files.writeString(
                    output.resolve("canonical-worker-fill-settlement.json"),
                    app.getBean(ObjectMapper.class)
                            .writeValueAsString(
                                    Map.of(
                                            "receipts",
                                            receipts,
                                            "canonicalAccount",
                                            account,
                                            "trades",
                                            2,
                                            "cashUSDT",
                                            "799.8",
                                            "positionBTC",
                                            "2",
                                            "externalIdsLinked",
                                            1,
                                            "duplicateQueriesOnOriginalReplay",
                                            0,
                                            "source",
                                            "controlled venue read only",
                                            "authority",
                                            "no PLACE/CANCEL or Risk admission claim")));
        }
    }
}
