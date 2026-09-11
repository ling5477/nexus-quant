package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;
import com.guidinglight.nexusquant.trading.infra.query.JdbcTradingQueryFacade;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 PG/V51 与独立服务 JVM 的并发证明；expected 只从 Trade/Order 重建，绝不读取 Position。 */
@EnabledIfSystemProperty(named = "nq.l5.projection", matches = "true")
class L5ProjectionConcurrencyTest {
    private static final Instant TIME = Instant.parse("2026-09-11T00:00:00Z");

    @Test void sequentialReverseTimeReplayAndBaseFeeRemainExact() throws Exception {
        try (var pg = B0Processes.Pg.start(); var f = new Fixture(pg)) {
            long account = f.account("sequential");
            String first = f.trade(account, "BTC-USDT", "BUY", "2", "100", "0.01", "BTC", TIME.plusSeconds(20));
            String second = f.trade(account, "BTC-USDT", "SELL", "0.5", "120", "0.02", "USDT", TIME);
            String third = f.trade(account, "BTC-USDC", "BUY", "0.3", "90", "0.01", "USDC", TIME.minusSeconds(20));
            for (String trade : List.of(first, second, third)) f.apply(trade);
            f.exact();
            decimal("1.49", f.scalar("SELECT qty FROM positions WHERE account_id=" + account + " AND symbol='BTC-USDT'"));
            decimal("1.79", f.balance(account, "BTC"));
            assertNotEquals(f.scalar("SELECT balance FROM account_snapshots WHERE account_id=" + account
                    + " AND currency='BTC' ORDER BY ts DESC,snapshot_id DESC LIMIT 1"), f.balance(account, "BTC"));
            String before = f.durable();
            for (String trade : List.of(third, first, second, first)) assertTrue(f.apply(trade));
            assertEquals(before, f.durable());
            assertEquals(1L, f.jdbc.queryForObject("SELECT cache_size FROM pg_sequences WHERE sequencename='account_snapshots_snapshot_id_seq'", Long.class));
            System.out.println("L5_PROJECTION_PASS sequential reverse_time base_fee multi_symbol replay");
        }
    }

    @Test void oneTwoAndFourJvmContendersApplyEveryUniqueFactOnce() throws Exception {
        try (var pg = B0Processes.Pg.start(); var f = new Fixture(pg)) {
            List<B0Processes.Child> children = new ArrayList<>();
            try {
                for (int i = 0; i < 4; i++) children.add(f.child("actor-" + i));
                assertEquals(4L, children.stream().map(c -> c.process.pid()).distinct().count());
                for (int concurrency : List.of(1, 2, 4)) {
                    long account = f.account("hot-" + concurrency);
                    // 首波没有 Position/快照行；在生产使用的币种锁上阻塞所有 JVM，并从 PG 验证等待数。
                    try (Connection gate = f.gate(account, "BTC")) {
                        for (int i = 0; i < concurrency; i++) {
                            children.get(i).startCommand("APPLY " + f.trade(account, "BTC-USDT", "BUY",
                                    "0.1", "100", "0.01", "USDT", TIME.plusSeconds(i)));
                        }
                        f.waiters(concurrency);
                        gate.rollback();
                        for (int i = 0; i < concurrency; i++) assertEquals("POSTED", children.get(i).result());
                    }
                    for (int wave = 1; wave < 10; wave++) {
                        for (int i = 0; i < concurrency; i++) children.get(i).startCommand("APPLY "
                                + f.trade(account, "BTC-USDT", "BUY", "0.1", "100", "0.01", "USDT",
                                TIME.minusSeconds(wave * 10L + i)));
                        for (int i = 0; i < concurrency; i++) assertEquals("POSTED", children.get(i).result());
                    }
                    f.exact();
                    decimal(String.valueOf(concurrency), f.balance(account, "BTC"));
                    String before = f.durable();
                    String replay = f.jdbc.queryForObject("SELECT trade_id FROM trades WHERE account_id=? ORDER BY trade_id LIMIT 1", String.class, account);
                    try (Connection gate = f.gate(account, "BTC")) {
                        for (int i = 0; i < concurrency; i++) children.get(i).startCommand("APPLY " + replay);
                        f.waiters(concurrency); gate.rollback();
                        for (int i = 0; i < concurrency; i++) assertEquals("IDEMPOTENT_HIT", children.get(i).result());
                    }
                    assertEquals(before, f.durable());
                    System.out.println("L5_PROJECTION_PASS concurrency=" + concurrency + " unique=" + concurrency * 10
                            + " observed_pg_waiters=" + concurrency + " replay_unchanged=true");
                }
                long account = f.account("same-fresh-fact");
                String trade = f.trade(account, "BTC-USDT", "BUY", "0.1", "100", "0.01", "USDT", TIME);
                try (Connection gate = f.gate(account, "BTC")) {
                    for (var child : children) child.startCommand("APPLY " + trade);
                    f.waiters(4); gate.rollback();
                    int posted = 0;
                    for (var child : children) {
                        String result = child.result();
                        assertTrue(List.of("POSTED", "IDEMPOTENT_HIT").contains(result));
                        if ("POSTED".equals(result)) posted++;
                    }
                    assertEquals(1, posted); f.exact(); decimal("0.1", f.balance(account, "BTC"));
                }
                System.out.println("L5_PROJECTION_PASS four_jvms_same_unapplied_fact applications=1");
            } finally {
                for (var child : children) child.close();
            }
        }
    }

    @Test void pausedOldWriterCannotPublishAfterNewerStateAndUnrelatedAssetsProgress() throws Exception {
        try (var pg = B0Processes.Pg.start(); var f = new Fixture(pg);
             var a = f.child("paused"); var b = f.child("same-base"); var c = f.child("other-asset");
             var d = f.child("other-account")) {
            long account = f.account("race");
            long other = f.account("other");
            String old = f.trade(account, "BTC-USDT", "BUY", "0.1", "100", "0", "USDT", TIME);
            String newer = f.trade(account, "BTC-USDC", "BUY", "0.2", "110", "0", "USDC", TIME.minusSeconds(60));
            assertEquals("ARMED", a.send("ARM findAssetPosition"));
            a.startCommand("APPLY " + old);
            f.cut(a);
            b.startCommand("APPLY " + newer);
            f.waiters(1);
            String isolated = f.trade(account, "ETH-USD", "BUY", "3", "30", "0", "USD", TIME);
            String separate = f.trade(other, "BTC-USDT", "BUY", "4", "40", "0", "USDT", TIME);
            assertEquals("POSTED", c.send("APPLY " + isolated));
            assertEquals("POSTED", d.send("APPLY " + separate));
            assertEquals(0, f.jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_id IN (?,?)", Integer.class, old, newer));
            f.release(a);
            assertEquals("POSTED", a.result()); assertEquals("POSTED", b.result());
            f.exact(); decimal("0.3", f.balance(account, "BTC"));
            assertTrue(f.jdbc.queryForObject("SELECT ts FROM account_snapshots WHERE account_id=? AND currency='BTC' ORDER BY snapshot_id DESC LIMIT 1",
                    Timestamp.class, account).toInstant().isBefore(TIME));
            System.out.println("L5_PROJECTION_PASS stale_writer_blocked different_asset_and_account_progress multi_symbol_snapshot");
        }
    }

    @Test void killAfterPositionReadReleasesDatabaseOwnershipAndSuccessorReplays() throws Exception {
        try (var pg = B0Processes.Pg.start(); var f = new Fixture(pg);
             var a = f.child("killed"); var b = f.child("survivor")) {
            long account = f.account("restart");
            String first = f.trade(account, "BTC-USDT", "BUY", "0.1", "100", "0.01", "USDT", TIME);
            String second = f.trade(account, "BTC-USDT", "BUY", "0.2", "100", "0.01", "USDT", TIME);
            assertEquals("ARMED", a.send("ARM findPosition"));
            a.startCommand("APPLY " + first); f.cut(a);
            b.startCommand("APPLY " + second); f.waiters(1);
            a.kill(); assertEquals("POSTED", b.result());
            assertEquals(0, f.jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_id=?", Integer.class, first));
            try (var successor = f.child("successor")) {
                assertNotEquals(a.process.pid(), successor.process.pid());
                assertEquals("POSTED", successor.send("APPLY " + first)); f.exact();
                String before = f.durable();
                assertEquals("IDEMPOTENT_HIT", successor.send("APPLY " + second));
                assertEquals("IDEMPOTENT_HIT", successor.send("APPLY " + first));
                assertEquals(before, f.durable()); decimal("0.3", f.balance(account, "BTC"));
            }
            System.out.println("L5_PROJECTION_PASS owner_killed concurrent_survivor successor_replay");
        }
    }

    @Test void incompleteLegacyApplicationAndMissingTransactionFailClosed() throws Exception {
        try (var pg = B0Processes.Pg.start(); var f = new Fixture(pg)) {
            long account = f.account("partial");
            String trade = f.trade(account, "BTC-USDT", "BUY", "0.1", "100", "0.01", "USDT", TIME);
            // 只用于不合法历史状态的拒绝证明，不将注入分录当作正常成交/验收事实。
            f.jdbc.update("""
                    INSERT INTO ledger_entries(entry_id,account_id,currency,delta,direction,ref_type,ref_id,idempotency_key,trace_id,ts)
                    VALUES ('lp-partial',?,'USDT',-10,'DEBIT','TRADE',?,?,'lp-partial',?)
                    """, account, trade, trade + ":LEDGER:1", Timestamp.from(TIME));
            String before = f.durable();
            assertThrows(IllegalStateException.class, () -> f.apply(trade));
            assertEquals(before, f.durable());
            assertThrows(IllegalStateException.class, () -> new JdbcLedgerPostingRepository(f.jdbc)
                    .lockSnapshotCurrencies(account, List.of("BTC")));
            var transaction = new TransactionTemplate(f.context.getBean(PlatformTransactionManager.class));
            transaction.setIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
            assertThrows(IllegalStateException.class, () -> transaction.execute(status -> f.apply(trade)));
            System.out.println("L5_PROJECTION_PASS partial_application_and_unsafe_transaction_rejected");
        }
    }

    static void decimal(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }

    static final class Fixture implements AutoCloseable {
        final B0Fixture database;
        final DriverManagerDataSource source;
        final JdbcTemplate jdbc;
        final AnnotationConfigApplicationContext context;
        final Path root;
        final Map<String, String> environment;
        int sequence;
        int observations;

        Fixture(B0Processes.Pg pg) throws Exception {
            Path parent = Files.createDirectories(B0Processes.root().resolve("backend/nq-app/target/l5-projection-remediation"));
            root = Files.createTempDirectory(parent, "direct-");
            database = B0Fixture.create(pg);
            source = new DriverManagerDataSource(database.url(), "postgres", "");
            jdbc = new JdbcTemplate(source);
            environment = B0Processes.cleanEnvironment();
            environment.put("NQ_B0_DB", database.url()); environment.put("NQ_B0_VENUE", "http://127.0.0.1:1");
            environment.put("NQ_B0_PROFILE", "b0-test");
            database.initialize(false, "http://127.0.0.1:1", environment);
            context = L5ProjectionProcessMain.context(source, new AtomicReference<>("NONE"));
            System.out.println("L5_PROJECTION_ROOT " + root);
        }

        long account(String name) {
            return jdbc.queryForObject("INSERT INTO accounts(account_code,venue,status) VALUES (?,'OKX','ACTIVE') RETURNING account_id", Long.class, name);
        }

        String trade(long account, String symbol, String side, String qty, String price, String fee, String currency, Instant ts) {
            int id = ++sequence;
            var quantity = new BigDecimal(qty); var executedPrice = new BigDecimal(price);
            // 直接仓储级证明的 source 由真实 NQ repository 形成；端到端 venue 成交另由原 C1 与恢复场景验证。
            new JdbcOrderRepository(jdbc).insert(new OrderRecord("lp-order-" + id, account, null, "OKX", symbol,
                    "lp-client-" + id, side, "LIMIT", executedPrice, quantity, "lp-venue-" + id, OrderStatus.FILLED,
                    "LP_DIRECT_SOURCE", "lp-trace-" + id, "SIM"), ts);
            var trade = new PaperTradeRecord("lp-trade-" + id, "lp-order-" + id, account, symbol, "OKX", "lp-venue-" + id,
                    "lp-fill-" + id, executedPrice, quantity, new BigDecimal(fee), currency, "lp-trace-" + id, ts);
            context.getBean(TradeRepository.class).insertWithRequiredEvent(trade);
            return trade.tradeId();
        }

        boolean apply(String trade) {
            return context.getBean(TradeLedgerPort.class).postTrade(L5ProjectionProcessMain.request(jdbc, trade)).idempotentHit();
        }

        B0Processes.Child child(String label) throws Exception {
            return new B0Processes.Child(L5ProjectionProcessMain.class, root.resolve(label), label, environment).awaitReady();
        }

        Connection gate(long account, String currency) throws Exception {
            Connection connection = source.getConnection(); connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1, "nq:account-snapshot:" + account + ":" + currency); statement.execute();
            }
            return connection;
        }

        void waiters(int count) throws Exception {
            long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND usename='nq_b0_app' AND wait_event_type='Lock'", Integer.class) < count) {
                assertTrue(System.nanoTime() < end, "missing actual PostgreSQL lock waiters"); Thread.sleep(20);
            }
        }

        void cut(B0Processes.Child child) throws Exception {
            Path marker = child.log.getParent().resolve("projection-cut.txt");
            long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (!Files.exists(marker)) {
                assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < end, "missing cut: " + child.log); Thread.sleep(20);
            }
        }

        void release(B0Processes.Child child) throws Exception { Files.writeString(child.log.getParent().resolve("projection-release.txt"), "release"); }
        BigDecimal scalar(String sql) { return jdbc.queryForObject(sql, BigDecimal.class); }
        BigDecimal balance(long account, String currency) {
            return new JdbcTradingQueryFacade(jdbc).queryAccount(account, "lp-query").orElseThrow().balances()
                    .stream().filter(row -> row.currency().equals(currency)).findFirst().orElseThrow().balance();
        }

        void exact() throws Exception {
            try (var reader = database.checker()) {
                // 使用独立、只读 MVCC 观察：quantity oracle 从 source Trade 与 Order.side 重建。
                reader.setAutoCommit(false); reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try (var statement = reader.createStatement(); var rows = statement.executeQuery("""
                        SELECT t.account_id,t.symbol,
                          SUM(CASE WHEN o.side='BUY' THEN t.qty ELSE -t.qty END
                            - CASE WHEN upper(t.fee_currency)=upper(split_part(replace(t.symbol,'/','-'),'-',1))
                                   THEN COALESCE(t.fee,0) ELSE 0 END) AS expected
                        FROM trades t JOIN orders o ON o.order_id=t.order_id GROUP BY t.account_id,t.symbol
                        """)) {
                    while (rows.next()) {
                        long account = rows.getLong("account_id"); String symbol = rows.getString("symbol");
                        try (var query = reader.prepareStatement("SELECT qty,available_qty FROM positions WHERE account_id=? AND symbol=?")) {
                            query.setLong(1, account); query.setString(2, symbol);
                            try (var position = query.executeQuery()) {
                                assertTrue(position.next());
                                decimal(rows.getBigDecimal("expected").toPlainString(), position.getBigDecimal("qty"));
                                decimal(rows.getBigDecimal("expected").toPlainString(), position.getBigDecimal("available_qty"));
                            }
                        }
                    }
                }
                try (var statement = reader.createStatement(); var rows = statement.executeQuery("""
                        SELECT t.account_id,split_part(replace(t.symbol,'/','-'),'-',1) AS currency,
                          SUM(CASE WHEN o.side='BUY' THEN t.qty ELSE -t.qty END
                            - CASE WHEN upper(t.fee_currency)=upper(split_part(replace(t.symbol,'/','-'),'-',1))
                                   THEN COALESCE(t.fee,0) ELSE 0 END) AS expected
                        FROM trades t JOIN orders o ON o.order_id=t.order_id GROUP BY t.account_id,currency
                        """)) {
                    while (rows.next()) decimal(rows.getBigDecimal("expected").toPlainString(), balance(rows.getLong("account_id"), rows.getString("currency")));
                }
                reader.commit();
            }
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM (SELECT idempotency_key FROM ledger_entries GROUP BY idempotency_key HAVING count(*)<>1) duplicates", Integer.class));
            assertEquals(0, jdbc.queryForObject("""
                    SELECT count(*) FROM trades t WHERE
                      (SELECT count(*) FROM ledger_entries l WHERE l.ref_id=t.trade_id)<>CASE WHEN t.fee>0 THEN 4 ELSE 2 END
                      OR (SELECT count(*) FROM event_store e WHERE e.event_type='TradeExecuted'
                        AND e.payload_json->'payload'->>'trade_id'=t.trade_id)<>1
                    """, Integer.class));
            Files.writeString(root.resolve("raw-state-" + ++observations + ".json"), durable());
        }

        String durable() {
            return jdbc.queryForObject("""
                    SELECT jsonb_build_object(
                      'trades',(SELECT jsonb_agg(to_jsonb(t) ORDER BY trade_id) FROM trades t),
                      'ledger',(SELECT jsonb_agg(to_jsonb(l) ORDER BY entry_id) FROM ledger_entries l),
                      'events',(SELECT jsonb_agg(to_jsonb(e) ORDER BY event_id) FROM event_store e),
                      'positions',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM positions p),
                      'snapshots',(SELECT jsonb_agg(to_jsonb(s) ORDER BY snapshot_id) FROM account_snapshots s),
                      'ledger_events',(SELECT jsonb_agg(to_jsonb(e) ORDER BY ledger_event_id) FROM ledger_events e))::text
                    """, String.class);
        }

        @Override public void close() throws Exception { context.close(); database.close(); }
    }
}
