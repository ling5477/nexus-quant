package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.ledger.application.service.TradeLedgerPostingService;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerRiskAuditRepository;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.scheduler.infra.jdbc.JdbcTradeRepository;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeReserve;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** 在独立临时PG中证明canonical账务消费；订单/成交是明确的层级夹具，不冒充完整Risk/Execution资格。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class SignedFeeLedgerPostgresIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}

    @Test
    void chargesZeroAndRebatesConserveEachCurrencyOnBuyAndSell() throws Exception {
        try (var f = new Fixture()) {
            for (String currency : List.of("USDT", "BTC")) {
                for (String fee : List.of("0.1", "0", "-0.1")) {
                    for (OrderSide side : OrderSide.values()) {
                        long account = f.account();
                        if (side == OrderSide.SELL) {
                            f.post(f.trade(account, OrderSide.BUY, d("0"), "USDT"));
                            f.post(f.trade(account, OrderSide.BUY, d("0"), "USDT"));
                        }
                        var request = f.trade(account, side, d(fee), currency);
                        assertTrue(f.ledger.postTrade(request).posted());
                        f.assertEconomics(
                                request,
                                side == OrderSide.SELL ? d("2") : d("0"),
                                side == OrderSide.SELL ? d("800") : d("1000"));
                        String after = f.snapshot(account);
                        assertTrue(f.ledger.postTrade(request).idempotentHit());
                        assertEquals(after, f.snapshot(account));
                    }
                }
            }
        }
    }

    @Test
    void feeRoundingKeepsSignAndDoesNotChangePositionForRoundedZero() throws Exception {
        try (var f = new Fixture()) {
            for (String fee :
                    List.of("0.000000005", "-0.000000005", "0.000000001", "-0.000000001")) {
                long account = f.account();
                var request = f.trade(account, OrderSide.BUY, d(fee), "BTC");
                assertTrue(f.ledger.postTrade(request).posted());
                f.assertEconomics(request, d("0"), d("1000"));
            }
        }
    }

    @Test
    void concurrentDuplicateWaitsOnRealPgLockAndPostsOnce() throws Exception {
        try (var f = new Fixture();
                var pool = Executors.newFixedThreadPool(2);
                Connection blocker = f.source.getConnection()) {
            var request = f.trade(f.account(), OrderSide.BUY, d("-0.1"), "USDT");
            blocker.setAutoCommit(false);
            try (var statement =
                    blocker.prepareStatement(
                            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "nq:account-environment:" + request.accountId());
                statement.setQueryTimeout(10);
                statement.execute();
            }
            CountDownLatch entered = new CountDownLatch(2);
            var first =
                    pool.submit(
                            () -> {
                                entered.countDown();
                                return f.ledger.postTrade(request);
                            });
            var second =
                    pool.submit(
                            () -> {
                                entered.countDown();
                                return f.ledger.postTrade(request);
                            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                int waiting = 0;
                // 以真实数据库等待锁状态建立并发证据；等待只做有界观察，不靠睡眠猜测两个writer相遇。
                while (waiting < 2 && System.nanoTime() < deadline) {
                    waiting =
                            f.jdbc.queryForObject(
                                    """
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname=current_database() AND pid<>pg_backend_pid()
                              AND wait_event_type='Lock' AND query LIKE '%pg_advisory_xact_lock%'
                            """,
                                    Integer.class);
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                }
                assertEquals(2, waiting);
            } finally {
                blocker.rollback();
            }
            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            assertTrue(a.posted() && b.posted());
            assertTrue(a.idempotentHit() != b.idempotentHit());
            f.assertEconomics(request, d("0"), d("1000"));
        }
    }

    @Test
    void deferredCommitFailureRollsBackLedgerProjectionEventsAndAudit() throws Exception {
        try (var f = new Fixture()) {
            var request = f.trade(f.account(), OrderSide.BUY, d("-0.1"), "BTC");
            String before = f.snapshot(request.accountId());
            f.jdbc.execute(
                    """
                    CREATE FUNCTION signed_fee_reject_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'synthetic signed fee commit rejection'; END $$
                    """);
            f.jdbc.execute(
                    """
                    CREATE CONSTRAINT TRIGGER signed_fee_reject_snapshot AFTER INSERT ON account_snapshots
                    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION signed_fee_reject_snapshot()
                    """);
            assertThrows(RuntimeException.class, () -> f.ledger.postTrade(request));
            assertEquals(before, f.snapshot(request.accountId()));
            assertEquals(
                    1,
                    f.jdbc.queryForObject(
                            "SELECT count(*) FROM trades WHERE trade_id=?",
                            Integer.class,
                            request.tradeId()));
            f.jdbc.execute("DROP TRIGGER signed_fee_reject_snapshot ON account_snapshots");
            assertTrue(f.ledger.postTrade(request).posted());
            f.assertEconomics(request, d("0"), d("1000"));
        }
    }

    @Test
    void committedUnknownResultConvergesInTwoFreshProcesses() throws Exception {
        try (var f = new Fixture()) {
            var request = f.trade(f.account(), OrderSide.BUY, d("-0.1"), "USDT");
            assertTrue(f.ledger.postTrade(request).posted());
            String committed = f.snapshot(request.accountId());
            // 已提交而调用方未接收结果的夹具，只重读并重放同一成交；不伪称真实供应方网络UNKNOWN。
            long first = f.restartAndReplay(request.tradeId());
            long second = f.restartAndReplay(request.tradeId());
            assertTrue(
                    first != second
                            && first != ProcessHandle.current().pid()
                            && second != ProcessHandle.current().pid());
            assertEquals(committed, f.snapshot(request.accountId()));
            f.assertEconomics(request, d("0"), d("1000"));
        }
    }

    @Test
    void migrationUpgradesWithoutChangingOldFactsAndRejectsInvalidRates() throws Exception {
        try (var f = new Fixture(false)) {
            var request = f.trade(f.account(), OrderSide.BUY, d("0.1"), "USDT");
            f.post(request);
            String before = f.snapshot(request.accountId());
            f.flyway("8").migrate();
            f.flyway("8").validate();
            assertEquals(before, f.snapshot(request.accountId()));
            assertEquals("8", f.flyway("8").info().current().getVersion().toString());
            // 用PG执行真实CHECK表达式的正负例；不创建虚假的策略版本或运行来绕过外键。
            f.jdbc.execute("CREATE TABLE signed_fee_check(fee_rate numeric, slippage_bps numeric)");
            String definition =
                    f.jdbc.queryForObject(
                            """
                    SELECT pg_get_constraintdef(oid) FROM pg_constraint
                    WHERE conname='chk_strategy_sim_decision_cost' AND conrelid='strategy_sim_decisions'::regclass
                    """,
                            String.class);
            f.jdbc.execute(
                    "ALTER TABLE signed_fee_check ADD CONSTRAINT copied_signed_fee_check "
                            + definition);
            for (String value : List.of("-0.1", "0", "0.1"))
                f.jdbc.update("INSERT INTO signed_fee_check VALUES (?,0)", d(value));
            for (String value : List.of("-1", "1", "-2", "2")) {
                assertThrows(
                        RuntimeException.class,
                        () -> f.jdbc.update("INSERT INTO signed_fee_check VALUES (?,0)", d(value)));
            }
            assertThrows(
                    RuntimeException.class,
                    () -> f.jdbc.update("INSERT INTO signed_fee_check VALUES (0,-1)"));
            assertThrows(
                    RuntimeException.class,
                    () -> f.jdbc.update("INSERT INTO signed_fee_check VALUES (0,10000)"));
        }
    }

    /** 复现独立审查的合法八位price/qty边界，并以PG现金和快照确认拒绝第二单后不透支。 */
    @Test
    void roundedPrincipalReserveRejectsOverdraftWhenRebateRoundsToZero() throws Exception {
        try (var f = new Fixture()) {
            BigDecimal firstPrice = d("999.99999999"),
                    secondPrice = d("1000.00000001"),
                    quantity = d("0.5");
            for (String value : List.of("0", "-0.000000000001")) {
                BigDecimal rate = d(value),
                        pending = SpotFeeReserve.buyCost(firstPrice.multiply(quantity), rate);
                long account = f.account(d("2000"));
                f.post(
                        f.trade(
                                account,
                                OrderSide.BUY,
                                BigDecimal.ZERO,
                                "USDT",
                                d("1000"),
                                BigDecimal.ONE));
                BigDecimal cash =
                        f.app.getBean(JdbcLedgerPostingRepository.class)
                                .currentBalance(account, "USDT", "SIM");
                BigDecimal position =
                        f.jdbc.queryForObject(
                                "SELECT qty FROM positions WHERE account_id=? AND symbol='BTC-USDT'",
                                BigDecimal.class,
                                account);
                assertEquals(0, d("1000").compareTo(cash));
                assertEquals(0, BigDecimal.ONE.compareTo(position));
                var second =
                        SpotTargetSizer.size(
                                BigDecimal.ONE,
                                new SpotTargetSizer.State(
                                        cash,
                                        position,
                                        quantity,
                                        BigDecimal.ZERO,
                                        pending,
                                        d("1000")),
                                new SpotTargetSizer.Rules(
                                        quantity,
                                        d("0.00000001"),
                                        quantity,
                                        BigDecimal.ONE,
                                        rate,
                                        d("0.0000000001")));
                assertTrue(
                        pending.add(SpotFeeReserve.buyCost(secondPrice.multiply(quantity), rate))
                                        .compareTo(d("1000"))
                                > 0);
                assertTrue(!second.executable());
                var first =
                        f.trade(
                                account,
                                OrderSide.BUY,
                                firstPrice.multiply(quantity).multiply(rate),
                                "USDT",
                                firstPrice,
                                quantity);
                f.post(first);
                assertEquals(
                        0,
                        d("500").compareTo(
                                        f.app.getBean(JdbcLedgerPostingRepository.class)
                                                .currentBalance(account, "USDT", "SIM")));
                assertEquals(
                        0,
                        d("500").compareTo(
                                        f.jdbc.queryForObject(
                                                "SELECT balance FROM account_snapshots WHERE account_id=? AND currency='USDT' ORDER BY snapshot_id DESC LIMIT 1",
                                                BigDecimal.class,
                                                account)));
                assertEquals(0, first.fee().signum());
                assertEquals(
                        2,
                        f.jdbc.queryForObject(
                                "SELECT count(*) FROM trades WHERE account_id=?",
                                Integer.class,
                                account));
                assertEquals(
                        2,
                        f.jdbc.queryForObject(
                                "SELECT count(*) FROM ledger_entries WHERE ref_id=?",
                                Integer.class,
                                first.tradeId()));
            }
        }
    }

    /** 所有表由正式Flyway创建，测试只拥有自己新建的数据库；关闭路径不接触验收库。 */
    static final class Fixture implements AutoCloseable {
        final ReleaseBaselineTestDatabase database;
        final DriverManagerDataSource source;
        final JdbcTemplate jdbc;
        final AnnotationConfigApplicationContext app;
        final TradeLedgerPort ledger;

        Fixture() throws Exception {
            this(true);
        }

        Fixture(boolean upgrade) throws Exception {
            database = ReleaseBaselineTestDatabase.create();
            source = database.source();
            jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(15);
            try {
                flyway(upgrade ? "8" : "7").migrate();
                flyway(upgrade ? "8" : "7").validate();
                app = context(source);
                ledger = app.getBean(TradeLedgerPort.class);
            } catch (Exception failure) {
                // 构造失败时try-with-resources尚未持有夹具，仍须释放本次拥有的临时数据库。
                try {
                    database.close();
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        Flyway flyway(String version) {
            return Flyway.configure()
                    .dataSource(source)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .target(version)
                    .load();
        }

        long account() {
            return account(d("1000"));
        }

        long account(BigDecimal budget) {
            long account =
                    jdbc.queryForObject(
                            "INSERT INTO accounts(account_code,venue,status) VALUES (?,'PAPER','ACTIVE') RETURNING account_id",
                            Long.class,
                            "signed-fee-" + UUID.randomUUID());
            app.getBean(SimCashFundingPort.class)
                    .fundOnce(account, "signed-fee-" + account, budget, "signed-fee-funding");
            return account;
        }

        TradeLedgerRequest trade(long account, OrderSide side, BigDecimal fee, String currency) {
            return trade(account, side, fee, currency, d("100"), d("1"));
        }

        TradeLedgerRequest trade(
                long account,
                OrderSide side,
                BigDecimal fee,
                String currency,
                BigDecimal price,
                BigDecimal quantity) {
            String id = UUID.randomUUID().toString();
            Instant at = Instant.parse("2026-10-13T00:00:00Z");
            new JdbcOrderRepository(jdbc)
                    .insert(
                            new OrderRecord(
                                    "ord-" + id,
                                    account,
                                    null,
                                    "OKX",
                                    "BTC-USDT",
                                    "client-" + id,
                                    side.name(),
                                    "MARKET",
                                    price,
                                    quantity,
                                    "venue-" + id,
                                    OrderStatus.FILLED,
                                    "SIGNED_FEE_ISOLATED_LEDGER",
                                    "trc-" + id,
                                    "SIM"),
                            at);
            app.getBean(TradeRepository.class)
                    .insertWithRequiredEvent(
                            new PaperTradeRecord(
                                    "trd-" + id,
                                    "ord-" + id,
                                    account,
                                    "BTC-USDT",
                                    "OKX",
                                    "venue-" + id,
                                    "fill-" + id,
                                    price,
                                    quantity,
                                    fee,
                                    currency,
                                    "trc-" + id,
                                    at));
            // 以数据库已舍入的成交事实组装记账请求，不把供应方高精度输入和持久化事实混用。
            return request(jdbc, "trd-" + id);
        }

        void post(TradeLedgerRequest request) {
            assertTrue(ledger.postTrade(request).posted());
        }

        void assertEconomics(
                TradeLedgerRequest request, BigDecimal priorPosition, BigDecimal priorCash) {
            BigDecimal qty =
                    priorPosition.add(
                            request.side() == OrderSide.BUY
                                    ? request.qty()
                                    : request.qty().negate());
            if ("BTC".equals(request.feeCurrency())) qty = qty.subtract(request.fee());
            assertEquals(
                    0,
                    qty.compareTo(
                            jdbc.queryForObject(
                                    "SELECT qty FROM positions WHERE account_id=? AND symbol='BTC-USDT'",
                                    BigDecimal.class,
                                    request.accountId())));
            if (priorPosition.signum() == 0
                    && request.side() == OrderSide.BUY
                    && qty.signum() > 0) {
                BigDecimal average =
                        request.price()
                                .multiply(request.qty())
                                .divide(qty, 8, RoundingMode.HALF_UP);
                assertEquals(
                        0,
                        average.compareTo(
                                jdbc.queryForObject(
                                        "SELECT avg_price FROM positions WHERE account_id=? AND symbol='BTC-USDT'",
                                        BigDecimal.class,
                                        request.accountId())));
            }
            BigDecimal cash = priorCash.add(request.side() == OrderSide.BUY ? d("-100") : d("100"));
            if ("USDT".equals(request.feeCurrency())) cash = cash.subtract(request.fee());
            assertEquals(
                    0,
                    cash.compareTo(
                            app.getBean(JdbcLedgerPostingRepository.class)
                                    .currentBalance(request.accountId(), "USDT", "SIM")));
            assertEquals(
                    request.fee().signum() == 0 ? 2 : 4,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM ledger_entries WHERE ref_id=?",
                            Integer.class,
                            request.tradeId()));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM (SELECT currency FROM ledger_entries WHERE ref_id=? GROUP BY currency HAVING sum(delta)<>0) unbalanced",
                            Integer.class,
                            request.tradeId()));
            if (request.fee().signum() != 0)
                assertEquals(
                        0,
                        request.fee()
                                .negate()
                                .compareTo(
                                        jdbc.queryForObject(
                                                "SELECT delta FROM ledger_entries WHERE idempotency_key=?",
                                                BigDecimal.class,
                                                request.tradeId() + ":LEDGER:FEE_1")));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM orders WHERE trade_env='LIVE'", Integer.class));
        }

        String snapshot(long account) {
            return jdbc.queryForObject(
                    """
                    SELECT jsonb_build_object(
                      'entries',(SELECT jsonb_agg(to_jsonb(e) ORDER BY entry_id) FROM ledger_entries e WHERE account_id=?),
                      'positions',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM positions p WHERE account_id=?),
                      'snapshots',(SELECT jsonb_agg(to_jsonb(s) ORDER BY snapshot_id) FROM account_snapshots s WHERE account_id=?),
                      'ledgerEvents',(SELECT jsonb_agg(to_jsonb(e) ORDER BY ledger_event_id) FROM ledger_events e),
                      'events',(SELECT jsonb_agg(to_jsonb(e) ORDER BY event_id) FROM event_store e),
                      'audit',(SELECT jsonb_agg(to_jsonb(a) ORDER BY id) FROM audit_logs a))::text
                    """,
                    String.class,
                    account,
                    account,
                    account);
        }

        long restartAndReplay(String trade) throws Exception {
            String executable =
                    Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
            var builder =
                    new ProcessBuilder(
                            executable,
                            "-cp",
                            System.getProperty("java.class.path"),
                            SignedFeeLedgerRestartProbeMain.class.getName(),
                            trade);
            builder.environment().put("NQ_SIGNED_FEE_DB_URL", database.url());
            builder.environment()
                    .put("NQ_SIGNED_FEE_DB_USER", System.getProperty("nq.postgres.smoke.user"));
            builder.environment()
                    .put(
                            "NQ_SIGNED_FEE_DB_PASSWORD",
                            System.getProperty("nq.postgres.smoke.password"));
            builder.redirectErrorStream(true);
            Process child = builder.start();
            try {
                assertTrue(child.waitFor(30, TimeUnit.SECONDS));
                String output =
                        new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(0, child.exitValue());
                assertTrue(output.contains("SIGNED_FEE_REPLAY=IDEMPOTENT_HIT"));
                return child.pid();
            } finally {
                if (child.isAlive()) child.destroyForcibly();
            }
        }

        @Override
        public void close() throws Exception {
            try {
                app.close();
            } finally {
                database.close();
            }
        }
    }

    /** 独立进程和父测试使用相同正式Spring事务装配，不使用内存账本替身。 */
    static AnnotationConfigApplicationContext context(DriverManagerDataSource source) {
        var context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(
                JdbcTemplate.class,
                () -> {
                    var jdbc = new JdbcTemplate(source);
                    jdbc.setQueryTimeout(15);
                    return jdbc;
                });
        context.registerBean(
                ObjectMapper.class,
                () ->
                        new ObjectMapper()
                                .findAndRegisterModules()
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        context.registerBean(
                "transactionManager",
                DataSourceTransactionManager.class,
                () -> new DataSourceTransactionManager(source));
        context.register(
                JdbcTradeRepository.class,
                EventStoreAppender.class,
                JdbcLedgerPostingRepository.class,
                JdbcLedgerRiskAuditRepository.class,
                TradeLedgerPostingService.class,
                SimCashFundingService.class);
        context.refresh();
        return context;
    }

    static TradeLedgerRequest request(JdbcTemplate jdbc, String trade) {
        return jdbc.queryForObject(
                """
                SELECT t.*,o.side FROM trades t JOIN orders o ON o.order_id=t.order_id WHERE trade_id=?
                """,
                (rs, row) ->
                        new TradeLedgerRequest(
                                rs.getString("trade_id"),
                                rs.getString("order_id"),
                                rs.getLong("account_id"),
                                rs.getString("symbol"),
                                OrderSide.valueOf(rs.getString("side")),
                                rs.getBigDecimal("price"),
                                rs.getBigDecimal("qty"),
                                rs.getBigDecimal("fee"),
                                rs.getString("fee_currency"),
                                rs.getString("trace_id"),
                                rs.getTimestamp("ts").toInstant(),
                                rs.getString("trade_env")),
                trade);
    }

    static BigDecimal d(String value) {
        return new BigDecimal(value);
    }
}
