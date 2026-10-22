package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantJdbcTemplateFactory;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantTransactionManager;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.application.service.SimAssetFundingService;
import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.ledger.application.service.TradeLedgerPostingService;
import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerRiskAuditRepository;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcSimAssetFundingRepository;
import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingPort;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.scheduler.infra.jdbc.JdbcTradeRepository;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

/** 全部事实位于随机库；订单/成交为层级夹具，正式 Ledger 消费不冒充 Risk/Execution 或全站验收。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class SimAssetFundingPostgresIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}

    private static final Instant AT = Instant.parse("2026-10-14T00:00:00Z");

    @Test
    void splitBudgetPostsBalancedCanonicalFactsAndRepeatsWithoutMutation() throws Exception {
        try (var f = new Fixture()) {
            Bound run = f.bound();
            SimAssetFundingPlan plan = plan("100", "0.1", "3", source("one"));
            assertEquals(plan, f.funding.fundOnce(run.account(), run.run(), plan, "funding-one"));
            equal("90.00000001", f.funding.assetBalance(run.account(), "USDT"));
            equal("3.33333333", f.funding.assetBalance(run.account(), "BNB"));
            f.assertPosting(run, plan);
            String before = f.snapshot(run.account());
            assertEquals(
                    plan, f.funding.fundOnce(run.account(), run.run(), plan, "new-retry-trace"));
            assertEquals(before, f.snapshot(run.account()));
            assertEquals(plan, f.funding.findPlan(run.account(), run.run()).orElseThrow());
        }
    }

    @Test
    void replayAfterActualCanonicalTradeNeverResetsCashOrAsset() throws Exception {
        try (var f = new Fixture()) {
            Bound run = f.bound();
            SimAssetFundingPlan plan = plan("100", "0.1", "600", source("trade"));
            f.funding.fundOnce(run.account(), run.run(), plan, "funding-trade");
            f.jdbc.update(
                    "UPDATE paper_trading_runs SET status='RUNNING' WHERE paper_run_id=?",
                    run.run());
            f.buy(run.account());
            BigDecimal cash = f.funding.assetBalance(run.account(), "USDT");
            assertEquals(0, plan.cashBudget().subtract(d("10")).compareTo(cash));
            String before = f.snapshot(run.account());
            f.funding.fundOnce(run.account(), run.run(), plan, "funding-trade-replay");
            assertEquals(before, f.snapshot(run.account()));
            assertEquals(0, cash.compareTo(f.funding.assetBalance(run.account(), "USDT")));
            assertEquals(
                    0,
                    plan.assetQuantity().compareTo(f.funding.assetBalance(run.account(), "BNB")));
        }
    }

    @Test
    void rejectsBudgetAndSourceIdentityDriftWithoutPostingMoreMoney() throws Exception {
        try (var f = new Fixture()) {
            Bound run = f.bound();
            SimAssetFundingPlan original = plan("100", "0.1", "600", source("original"));
            f.funding.fundOnce(run.account(), run.run(), original, "funding-drift");
            String before = f.snapshot(run.account());
            for (SimAssetFundingPlan changed :
                    List.of(
                            plan("101", "0.1", "600", source("original")),
                            plan("100", "0.2", "600", source("original")),
                            plan("100", "0.1", "601", source("original")),
                            plan("100", "0.1", "600", source("other")))) {
                assertThrows(
                        IllegalStateException.class,
                        () -> f.funding.fundOnce(run.account(), run.run(), changed, "drift"));
                assertEquals(before, f.snapshot(run.account()));
            }
            Bound other = f.bound();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            f.funding.fundOnce(
                                    other.account(), run.run(), original, "wrong-binding"));
            assertEquals(before, f.snapshot(run.account()));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            f.jdbc.update(
                                    "UPDATE sim_asset_funding_receipts SET total_budget=101 WHERE paper_run_id=?",
                                    run.run()));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            f.jdbc.update(
                                    "DELETE FROM sim_asset_funding_receipts WHERE paper_run_id=?",
                                    run.run()));
        }
    }

    @Test
    void missingOriginalEventAndSnapshotFailClosedInsteadOfRepairing() throws Exception {
        try (var f = new Fixture()) {
            for (String table : List.of("ledger_events", "account_snapshots")) {
                Bound run = f.bound();
                var plan = plan("100", "0.1", "600", source(table));
                f.funding.fundOnce(run.account(), run.run(), plan, "funding-incomplete");
                // 只在临时库人为删去可删除投影/事件制造反例，正式服务不得补造丢失事实。
                if (table.equals("ledger_events"))
                    f.jdbc.update(
                            "DELETE FROM ledger_events WHERE entry_id=(SELECT asset_entry_id FROM sim_asset_funding_receipts WHERE paper_run_id=?)",
                            run.run());
                else
                    f.jdbc.update(
                            "DELETE FROM account_snapshots WHERE account_id=? AND currency='BNB'",
                            run.account());
                String broken = f.snapshot(run.account());
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                f.funding.fundOnce(
                                        run.account(), run.run(), plan, "incomplete-retry"));
                assertEquals(broken, f.snapshot(run.account()));
            }
        }
    }

    @Test
    void requiresNewBoundPaperSimAndChecksAllExistingAssetFacts() throws Exception {
        try (var f = new Fixture()) {
            var plan = plan("100", "0.1", "600", source("empty"));
            for (String invalid :
                    List.of(
                            "LIVE",
                            "NON_PAPER",
                            "DISABLED",
                            "UNBOUND",
                            "RUNNING",
                            "LEGACY_FUND",
                            "THIRD_ASSET",
                            "POSITION",
                            "ORDER",
                            "SNAPSHOT")) {
                Bound run = f.bound();
                switch (invalid) {
                    case "LIVE" ->
                            f.jdbc.update(
                                    "UPDATE paper_trading_runs SET trade_env='LIVE' WHERE paper_run_id=?",
                                    run.run());
                    case "NON_PAPER" ->
                            f.jdbc.update(
                                    "UPDATE accounts SET venue='BINANCE' WHERE account_id=?",
                                    run.account());
                    case "DISABLED" ->
                            f.jdbc.update(
                                    "UPDATE accounts SET status='DISABLED' WHERE account_id=?",
                                    run.account());
                    case "UNBOUND" -> {
                        Bound unrelated = f.bound();
                        run = new Bound(unrelated.account(), run.run());
                    }
                    case "RUNNING" ->
                            f.jdbc.update(
                                    "UPDATE paper_trading_runs SET status='RUNNING' WHERE paper_run_id=?",
                                    run.run());
                    case "LEGACY_FUND" ->
                            f.app.getBean(SimCashFundingPort.class)
                                    .fundOnce(run.account(), run.run(), d("100"), "legacy");
                    case "THIRD_ASSET" ->
                            f.jdbc.update(
                                    "INSERT INTO ledger_entries(entry_id,account_id,currency,delta,balance_after,direction,ref_type,ref_id,idempotency_key,trace_id,ts) VALUES(?,?,'BNB',1,1,'CREDIT','SIM_FUNDING_CASH',?,?,'existing-bnb',now())",
                                    "le-" + UUID.randomUUID(),
                                    run.account(),
                                    run.run(),
                                    run.run() + ":EXISTING_BNB");
                    case "POSITION" ->
                            f.jdbc.update(
                                    "INSERT INTO positions(account_id,symbol,trace_id) VALUES(?,'BNB-USDT','existing-zero-position')",
                                    run.account());
                    case "ORDER" -> f.order(run.account(), "REJECTED");
                    case "SNAPSHOT" ->
                            f.jdbc.update(
                                    "INSERT INTO account_snapshots(account_id,currency,balance,available,frozen,ts,trace_id) VALUES(?,'BNB',0,0,0,now(),'existing-zero-snapshot')",
                                    run.account());
                    default -> throw new IllegalStateException();
                }
                Bound rejected = run;
                String before = f.snapshot(run.account());
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                f.funding.fundOnce(
                                        rejected.account(),
                                        rejected.run(),
                                        plan,
                                        "reject-existing"));
                assertEquals(before, f.snapshot(run.account()));
            }
            Bound malformed = f.bound();
            for (String json : List.of("{}", "[]", "null", "{broken")) {
                String before = f.snapshot(malformed.account());
                var request = plan("100", "0.1", "600", json);
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                f.funding.fundOnce(
                                        malformed.account(),
                                        malformed.run(),
                                        request,
                                        "bad-source"));
                assertEquals(before, f.snapshot(malformed.account()));
            }
        }
    }

    @Test
    void partialPostingFailureRollsBackBothCurrenciesReceiptEventsAndSnapshots() throws Exception {
        try (var f = new Fixture()) {
            Bound run = f.bound();
            var plan = plan("100", "0.1", "600", source("rollback"));
            String before = f.snapshot(run.account());
            f.database.admin.execute(
                    "CREATE FUNCTION sim_funding_fail_asset() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.currency='BNB' THEN RAISE EXCEPTION 'synthetic asset insertion rejection'; END IF; RETURN NEW; END $$");
            f.database.admin.execute(
                    "CREATE TRIGGER sim_funding_fail_asset BEFORE INSERT ON ledger_entries FOR EACH ROW EXECUTE FUNCTION sim_funding_fail_asset()");
            var failure = assertThrows(
                    RuntimeException.class,
                    () -> f.funding.fundOnce(run.account(), run.run(), plan, "funding-rollback"));
            assertTrue(rootMessage(failure).contains("synthetic asset insertion rejection"), failure.toString());
            assertEquals(before, f.snapshot(run.account()));
            f.database.admin.execute("DROP TRIGGER sim_funding_fail_asset ON ledger_entries");
            f.funding.fundOnce(run.account(), run.run(), plan, "funding-after-rollback");
            f.assertPosting(run, plan);
        }
    }

    @Test
    void deferredCommitFailureRollsBackAllInitializationFacts() throws Exception {
        try (var f = new Fixture()) {
            Bound run = f.bound();
            var plan = plan("100", "0.1", "600", source("deferred"));
            String before = f.snapshot(run.account());
            f.database.admin.execute(
                    "CREATE FUNCTION sim_funding_fail_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic funding commit rejection'; END $$");
            f.database.admin.execute(
                    "CREATE CONSTRAINT TRIGGER sim_funding_fail_commit AFTER INSERT ON sim_asset_funding_receipts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION sim_funding_fail_commit()");
            var failure = assertThrows(
                    RuntimeException.class,
                    () -> f.funding.fundOnce(run.account(), run.run(), plan, "funding-deferred"));
            assertTrue(rootMessage(failure).contains("synthetic funding commit rejection"), failure.toString());
            assertEquals(before, f.snapshot(run.account()));
        }
    }

    @Test
    void actualPgMutexSerializesConcurrentFirstFundingAndRejectsDifferentPlan() throws Exception {
        try (var f = new Fixture();
                var pool = Executors.newFixedThreadPool(2);
                Connection blocker = f.source.getConnection()) {
            Bound run = f.bound();
            var plan = plan("100", "0.1", "600", source("concurrent"));
            blocker.setAutoCommit(false);
            try (var lock =
                    blocker.prepareStatement(
                            "SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                lock.setString(1, "nq:account-environment:" + run.account());
                lock.setQueryTimeout(10);
                lock.execute();
            }
            CountDownLatch entered = new CountDownLatch(2);
            var one =
                    pool.submit(
                            () -> {
                                entered.countDown();
                                return f.database.as(f.identity, () -> f.funding.fundOnce(
                                        run.account(), run.run(), plan, "concurrent-one"));
                            });
            var two =
                    pool.submit(
                            () -> {
                                entered.countDown();
                                return f.database.as(f.identity, () -> f.funding.fundOnce(
                                        run.account(), run.run(), plan, "concurrent-two"));
                            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                int waiters = 0;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                while (waiters < 2 && System.nanoTime() < deadline) {
                    waiters =
                            f.jdbc.queryForObject(
                                    "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND wait_event_type='Lock' AND query LIKE '%pg_advisory_xact_lock%'",
                                    Integer.class);
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                }
                assertEquals(2, waiters);
                System.out.println(
                        "SIM_ASSET_FUNDING_LOCK_PROOF waiters="
                                + waiters
                                + " account="
                                + run.account()
                                + " database="
                                + f.database
                                        .url()
                                        .substring(f.database.url().lastIndexOf('/') + 1));
            } finally {
                blocker.rollback();
            }
            assertEquals(plan, one.get(15, TimeUnit.SECONDS));
            assertEquals(plan, two.get(15, TimeUnit.SECONDS));
            f.assertPosting(run, plan);
            String before = f.snapshot(run.account());
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            f.funding.fundOnce(
                                    run.account(),
                                    run.run(),
                                    plan("101", "0.1", "600", source("concurrent")),
                                    "drift"));
            assertEquals(before, f.snapshot(run.account()));
        }
    }

    @Test
    void v13UpgradePreservesExistingFactsAndValidatesRepeatMigrate() throws Exception {
        try (var f = new Fixture("13")) {
            Bound run = f.bound();
            equal("100", HistoricalSimCashFundingFixture.fundOnce(
                    f.jdbc, run.account(), run.run(), d("100")));
            String before = f.snapshotWithoutReceipt(run.account());
            f.flyway("14").migrate();
            f.flyway("14").validate();
            assertEquals(before, f.snapshotWithoutReceipt(run.account()));
            assertEquals(0, f.flyway("14").migrate().migrationsExecuted);
            assertEquals("14", f.flyway("14").info().current().getVersion().toString());
            assertEquals(
                    0,
                    f.jdbc.queryForObject(
                            "SELECT count(*) FROM sim_asset_funding_receipts", Integer.class));
            // 直接保留旧 Schema 与现代读取不兼容的诊断证据，不把它当资金业务失败。
            var incompatible = assertThrows(BadSqlGrammarException.class,
                    () -> new JdbcLedgerPostingRepository(f.jdbc).assertAccountEnvironment(run.account(), "SIM"));
            assertEquals("42703", incompatible.getSQLException().getSQLState());
            assertTrue(incompatible.getSQLException().getMessage().contains("ta.tenant_id"));
            assertEquals(before, f.snapshotWithoutReceipt(run.account()));
            System.out.println("SIM_FUNDING_HISTORICAL V13_TO_V14=PASS SQLSTATE="
                    + incompatible.getSQLException().getSQLState() + " facts=UNCHANGED");

            // 历史迁移验收已在 V14 完成；现代端口只在继续正向迁移后的受限租户环境运行。
            f.database.migrate(null);
            f.openModernRuntime();
            assertEquals(0, f.flyway(null).info().pending().length);
            assertTrue(f.flyway(null).info().current().getVersion().compareTo(
                    org.flywaydb.core.api.MigrationVersion.fromVersion("14")) > 0);
            assertEquals(before, f.snapshotWithoutReceipt(run.account()));
            equal("100", f.app.getBean(SimCashFundingPort.class).cashBalance(run.account()));
            equal("100", f.app.getBean(SimCashFundingPort.class)
                    .fundOnce(run.account(), run.run(), d("100"), "modern-cash-replay"));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            f.funding.fundOnce(
                                    run.account(),
                                    run.run(),
                                    plan("100", "0.1", "600", source("upgrade")),
                                    "cannot-reset"));
            assertEquals(before, f.snapshotWithoutReceipt(run.account()));
        }
    }

    @Test
    void actualMigrationCheckExpressionsRejectBadSourceUnitTimeAndBudget() throws Exception {
        try (var f = new Fixture("14")) {
            // 本随机库内复制真实 CHECK 到无外键/trigger 的证明表，隔离约束 oracle 与正式分录装配的影响。
            f.jdbc.execute(
                    "CREATE TABLE sim_funding_constraint_probe(LIKE sim_asset_funding_receipts INCLUDING CONSTRAINTS)");
            String sql =
                    """
                    INSERT INTO sim_funding_constraint_probe(paper_run_id,account_id,total_budget,quote_currency,asset_currency,
                        asset_budget_fraction,conversion_price,conversion_price_unit,observed_at,visible_at,as_of,source_sha256,source_json,
                        funded_at,trace_id,cash_entry_id,cash_contra_entry_id,asset_entry_id,asset_contra_entry_id)
                    VALUES('constraint-probe',1,?,'USDT','BNB',0.1,600,?,?::timestamptz,?::timestamptz,?::timestamptz,?,?,now(),'probe','c1','c2','a1','a2')
                    """;
            var valid = plan("100", "0.1", "600", source("constraint"));
            String at = AT.toString();
            f.jdbc.update(
                    sql,
                    d("100"),
                    "USDT_PER_BNB",
                    at,
                    at,
                    at,
                    valid.sourceSha256(),
                    valid.sourceJson());
            for (String budget : List.of("0", "-1", "100000.00000001")) {
                assertThrows(
                        RuntimeException.class,
                        () ->
                                f.jdbc.update(
                                        sql,
                                        d(budget),
                                        "USDT_PER_BNB",
                                        at,
                                        at,
                                        at,
                                        valid.sourceSha256(),
                                        valid.sourceJson()));
            }
            assertThrows(
                    RuntimeException.class,
                    () ->
                            f.jdbc.update(
                                    sql,
                                    d("100"),
                                    "BNB_PER_USDT",
                                    at,
                                    at,
                                    at,
                                    valid.sourceSha256(),
                                    valid.sourceJson()));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            f.jdbc.update(
                                    sql,
                                    d("100"),
                                    "USDT_PER_BNB",
                                    at,
                                    AT.plusSeconds(1).toString(),
                                    at,
                                    valid.sourceSha256(),
                                    valid.sourceJson()));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            f.jdbc.update(
                                    sql,
                                    d("100"),
                                    "USDT_PER_BNB",
                                    at,
                                    at,
                                    at,
                                    "0".repeat(64),
                                    valid.sourceJson()));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            f.jdbc.update(
                                    sql,
                                    d("100"),
                                    "USDT_PER_BNB",
                                    at,
                                    at,
                                    at,
                                    valid.sourceSha256(),
                                    valid.sourceJson() + " "));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            f.jdbc.update(
                                    sql,
                                    d("100"),
                                    "USDT_PER_BNB",
                                    at,
                                    at,
                                    at,
                                    valid.sourceSha256(),
                                    "[]"));
            assertEquals(
                    1,
                    f.jdbc.queryForObject(
                            "SELECT count(*) FROM sim_funding_constraint_probe", Integer.class));
        }
    }

    @Test
    void committedInitializationSurvivesTwoDistinctJvmReplaysAfterTrade() throws Exception {
        try (var f = new Fixture()) {
            Bound run = f.bound();
            var plan = plan("100", "0.1", "600", source("restart"));
            f.funding.fundOnce(run.account(), run.run(), plan, "funding-restart");
            f.buy(run.account());
            String before = f.snapshot(run.account());
            long a = f.restart(run), b = f.restart(run);
            assertNotEquals(a, b);
            assertNotEquals(ProcessHandle.current().pid(), a);
            assertEquals(before, f.snapshot(run.account()));
            f.assertPosting(run, plan);
        }
    }

    static AnnotationConfigApplicationContext context(DataSource source) {
        var app = new AnnotationConfigApplicationContext();
        var transactions = new TenantTransactionManager(source);
        app.register(Transactions.class);
        app.registerBean(
                JdbcTemplate.class,
                () -> {
                    var jdbc = TenantJdbcTemplateFactory.create(source, transactions);
                    jdbc.setQueryTimeout(15);
                    return jdbc;
                });
        app.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        app.registerBean(
                "transactionManager",
                TenantTransactionManager.class,
                () -> transactions);
        app.register(
                JdbcLedgerPostingRepository.class,
                JdbcSimAssetFundingRepository.class,
                SimAssetFundingService.class,
                SimCashFundingService.class,
                JdbcTradeRepository.class,
                EventStoreAppender.class,
                JdbcLedgerRiskAuditRepository.class,
                TradeLedgerPostingService.class);
        app.refresh();
        return app;
    }

    private record Bound(long account, String run) {}

    private static final class Fixture implements AutoCloseable {
        final TenantIsolationTestDatabase database;
        DataSource source;
        JdbcTemplate jdbc;
        AnnotationConfigApplicationContext app;
        SimAssetFundingPort funding;
        TenantIdentity identity;
        TenantContext.Scope scope;
        final String username = "sim-funding-" + UUID.randomUUID();

        Fixture() throws Exception {
            this(null);
        }

        Fixture(String historicalVersion) throws Exception {
            database = TenantIsolationTestDatabase.create(
                    historicalVersion,
                    System.getProperty("nq.postgres.smoke.url"),
                    System.getProperty("nq.postgres.smoke.user"),
                    System.getProperty("nq.postgres.smoke.password"));
            source = database.database.source();
            jdbc = database.admin;
            jdbc.setQueryTimeout(15);
            try {
                if (historicalVersion == null) openModernRuntime();
            } catch (Exception failure) {
                try {
                    close();
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        void openModernRuntime() {
            if (database.pool == null) database.runtime();
            // 两个资金线程必须同时抵达 PostgreSQL advisory lock，不能在连接池前串行。
            database.pool.setMaximumPoolSize(8);
            source = database.runtimeDataSource();
            identity = database.local(username);
            scope = TenantContext.open(identity);
            app = context(source);
            jdbc = app.getBean(JdbcTemplate.class);
            funding = app.getBean(SimAssetFundingPort.class);
        }

        Flyway flyway(String version) {
            return database.migration(version);
        }

        Bound bound() {
            String id = UUID.randomUUID().toString(),
                    strategy = "sf-s-" + id,
                    version = "sf-v-" + id,
                    research = "sf-r-" + id,
                    config = "sf-c-" + id,
                    backtest = "sf-b-" + id,
                    publish = "sf-p-" + id,
                    run = "sf-run-" + id;
            long account =
                    jdbc.queryForObject(
                            "INSERT INTO accounts(account_code,venue,status) VALUES(?,'PAPER','ACTIVE') RETURNING account_id",
                            Long.class,
                            "sf-account-" + id);
            // 仅初始化层的正式外键关联夹具；没有声称这些空快照通过研究、发布或风控验收。
            jdbc.update(
                    "INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version) VALUES(?,?,?,'SPOT_SMA_TARGET_V1','PAPER',?,'SIM',false,'{}',1)",
                    strategy,
                    strategy,
                    strategy,
                    account);
            jdbc.update(
                    "INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,param_snapshot_json,config_snapshot_json,source_snapshot_json,checksum) VALUES(?,?,1,'funding','ACTIVE','{}','{}','{}',?)",
                    version,
                    strategy,
                    "a".repeat(64));
            jdbc.update(
                    "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES(?,?,?,'{}')",
                    research,
                    strategy,
                    research);
            jdbc.update(
                    "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES(?,?,?)",
                    config,
                    research,
                    config);
            jdbc.update(
                    "INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,strategy_version_id,status,strategy_snapshot,backtest_config_snapshot,requested_at) VALUES(?,?,?,?,?,'CREATED','{}','{}',now())",
                    backtest,
                    config,
                    research,
                    strategy,
                    version);
            jdbc.update(
                    "INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name,strategy_version_id) VALUES(?,?,?,?,?,'SUCCEEDED','funding',?)",
                    publish,
                    backtest,
                    research,
                    config,
                    strategy,
                    version);
            jdbc.update(
                    "INSERT INTO paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id) VALUES(?,?,?,'CREATED','SIM','BINANCE','SPOT','BTC-USDT','1m','synthetic-funding-test',now(),now(),?)",
                    run,
                    publish,
                    version,
                    account);
            return new Bound(account, run);
        }

        String order(long account, String status) {
            String id = UUID.randomUUID().toString(), order = "ord-" + id;
            new JdbcOrderRepository(jdbc)
                    .insert(
                            new OrderRecord(
                                    order,
                                    account,
                                    null,
                                    "PAPER",
                                    "BTC-USDT",
                                    "client-" + id,
                                    "BUY",
                                    "MARKET",
                                    d("10"),
                                    BigDecimal.ONE,
                                    "venue-" + id,
                                    OrderStatus.valueOf(status),
                                    "SIM_ASSET_FUNDING_TEST",
                                    "trace-" + id,
                                    "SIM"),
                            AT);
            return order;
        }

        void buy(long account) {
            String order = order(account, "FILLED"),
                    id = UUID.randomUUID().toString(),
                    trade = "trd-" + id;
            // PAPER 层级成交夹具不能借用仅支持 OKX 的必需事件恢复入口；后续资金变化由正式 Ledger 事务产生。
            app.getBean(TradeRepository.class)
                    .insert(
                            new PaperTradeRecord(
                                    trade,
                                    order,
                                    account,
                                    "BTC-USDT",
                                    "PAPER",
                                    "venue-" + id,
                                    "fill-" + id,
                                    d("10"),
                                    BigDecimal.ONE,
                                    BigDecimal.ZERO,
                                    "USDT",
                                    "trace-" + id,
                                    AT));
            assertTrue(
                    app.getBean(TradeLedgerPort.class)
                            .postTrade(SignedFeeLedgerPostgresIntegrationTest.request(jdbc, trade))
                            .posted());
        }

        void assertPosting(Bound run, SimAssetFundingPlan plan) {
            assertEquals(
                    4,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM ledger_entries WHERE account_id=? AND ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')",
                            Integer.class,
                            run.account()));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM (SELECT currency FROM ledger_entries WHERE account_id=? GROUP BY currency HAVING sum(delta)<>0) bad",
                            Integer.class,
                            run.account()));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM sim_asset_funding_receipts WHERE account_id=?",
                            Integer.class,
                            run.account()));
            assertEquals(
                    0,
                    plan.totalBudget()
                            .compareTo(plan.cashBudget().add(plan.assetInitialQuoteValue())));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM orders WHERE trade_env='LIVE'", Integer.class));
        }

        String snapshotWithoutReceipt(long account) {
            return jdbc.queryForObject(
                    """
                    SELECT jsonb_build_object('entries',(SELECT jsonb_agg(to_jsonb(e) ORDER BY entry_id) FROM ledger_entries e WHERE account_id=?),
                        'snapshots',(SELECT jsonb_agg(to_jsonb(s) ORDER BY snapshot_id) FROM account_snapshots s WHERE account_id=?),
                        'positions',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM positions p WHERE account_id=?),
                        'orders',(SELECT jsonb_agg(to_jsonb(o) ORDER BY order_id) FROM orders o WHERE account_id=?),
                        'trades',(SELECT jsonb_agg(to_jsonb(t) ORDER BY trade_id) FROM trades t WHERE account_id=?),
                        'events',(SELECT jsonb_agg(to_jsonb(v) ORDER BY ledger_event_id) FROM ledger_events v JOIN ledger_entries e ON e.entry_id=v.entry_id WHERE e.account_id=?))::text
                    """,
                    String.class,
                    account,
                    account,
                    account,
                    account,
                    account,
                    account);
        }

        String snapshot(long account) {
            return snapshotWithoutReceipt(account)
                    + jdbc.queryForObject(
                            "SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY paper_run_id),'[]'::jsonb)::text FROM sim_asset_funding_receipts r WHERE account_id=?",
                            String.class,
                            account);
        }

        long restart(Bound run) throws Exception {
            var builder =
                    new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                            "-cp",
                            System.getProperty("java.class.path"),
                            SimAssetFundingRestartProbeMain.class.getName(),
                            Long.toString(run.account()),
                            run.run());
            builder.environment().put("NQ_SIM_ASSET_FUNDING_DB_URL", database.url());
            builder.environment()
                    .put(
                            "NQ_SIM_ASSET_FUNDING_DB_USER",
                            database.runtime);
            builder.environment()
                    .put(
                            "NQ_SIM_ASSET_FUNDING_DB_PASSWORD",
                            database.runtimePassword);
            builder.environment().put("NQ_SIM_ASSET_FUNDING_USERNAME", username);
            builder.redirectErrorStream(true);
            Process child = builder.start();
            try {
                assertTrue(child.waitFor(30, TimeUnit.SECONDS));
                String output =
                        new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(0, child.exitValue(), output);
                assertTrue(output.contains("SIM_ASSET_FUNDING_REPLAY=UNCHANGED"));
                System.out.println(
                        "SIM_ASSET_FUNDING_RESTART_PROOF parentPid="
                                + ProcessHandle.current().pid()
                                + " childPid="
                                + child.pid()
                                + " account="
                                + run.account()
                                + " unchanged=true");
                return child.pid();
            } finally {
                if (child.isAlive()) child.destroyForcibly();
            }
        }

        @Override
        public void close() throws Exception {
            try {
                if (app != null) app.close();
            } finally {
                if (scope != null) scope.close();
                database.close();
            }
        }
    }

    private static String rootMessage(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage();
    }

    private static String source(String identity) {
        return "{\"source\":\"SYNTHETIC_ISOLATED_FUNDING_TEST\",\"identity\":\"" + identity + "\"}";
    }

    private static SimAssetFundingPlan plan(
            String budget, String fraction, String price, String json) {
        try {
            String hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(json.getBytes(StandardCharsets.UTF_8)));
            return new SimAssetFundingPlan(
                    d(budget),
                    "USDT",
                    "BNB",
                    d(fraction),
                    d(price),
                    "USDT_PER_BNB",
                    AT,
                    AT.plusSeconds(1),
                    AT.plusSeconds(2),
                    hash,
                    json);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual));
    }
}
