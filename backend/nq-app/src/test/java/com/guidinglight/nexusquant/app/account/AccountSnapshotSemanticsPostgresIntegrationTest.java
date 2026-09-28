package com.guidinglight.nexusquant.app.account;

import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcAccountSnapshotReader;
import com.guidinglight.nexusquant.ledger.contracts.model.AccountBalanceBasis;
import com.guidinglight.nexusquant.ledger.contracts.model.AccountSnapshotProjection;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.scheduler.infra.jdbc.JdbcLedgerReconcileRepository;
import com.guidinglight.nexusquant.trading.infra.query.JdbcTradingQueryFacade;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 在隔离 PostgreSQL 16 schema 上验证旧行保留、环境过滤和发布序。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class AccountSnapshotSemanticsPostgresIntegrationTest {
    @Test
    void migrationAndLatestLiveProjectionKeepHistoricalAmbiguity() throws Exception {
        String url = System.getProperty("nq.postgres.smoke.url", "");
        String user = System.getProperty("nq.postgres.smoke.user", "");
        String password = System.getProperty("nq.postgres.smoke.password", "");
        assertTrue(!url.isBlank() && !user.isBlank() && !password.isBlank(), "disposable PG16 required");
        URI uri = URI.create(url.substring("jdbc:".length()));
        assertTrue(url.startsWith("jdbc:postgresql://") && Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                && uri.getRawQuery() == null && uri.getFragment() == null, "loopback database required");
        String schema = "snapshot_semantics_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                url + "?connectTimeout=5&socketTimeout=30&currentSchema=" + schema, user, password);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DriverManagerDataSource adminSource = new DriverManagerDataSource(
                url + "?connectTimeout=5&socketTimeout=30", user, password);
        JdbcTemplate admin = new JdbcTemplate(adminSource);
        try {
            Flyway old = Flyway.configure().dataSource(adminSource).locations("classpath:db/migration")
                    .schemas(schema).defaultSchema(schema).createSchemas(true).target("54").load();
            old.migrate();
            old.validate();
            assertEquals("54", old.info().current().getVersion().getVersion());
            long accountId = jdbc.queryForObject("""
                    INSERT INTO accounts(account_code,venue,status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id
                    """, Long.class, "synthetic-" + UUID.randomUUID());
            jdbc.update("""
                    INSERT INTO account_snapshots(account_id,currency,balance,available,frozen,ts,trace_id)
                    VALUES (?,'BTC',1,1,0,?,'legacy')
                    """, accountId, Timestamp.from(Instant.parse("2026-08-26T00:00:00Z")));
            long legacyId = jdbc.queryForObject("SELECT max(snapshot_id) FROM account_snapshots", Long.class);

            Flyway latest = Flyway.configure().dataSource(adminSource).locations("classpath:db/migration")
                    .schemas(schema).defaultSchema(schema).createSchemas(true).load();
            latest.migrate();
            latest.validate();
            assertEquals("56", latest.info().current().getVersion().getVersion());
            assertNull(jdbc.queryForObject("SELECT trade_env FROM account_snapshots WHERE snapshot_id=?",
                    String.class, legacyId));
            assertNull(jdbc.queryForObject("SELECT recorded_at FROM account_snapshots WHERE snapshot_id=?",
                    Timestamp.class, legacyId));

            JdbcLedgerPostingRepository writer = new JdbcLedgerPostingRepository(jdbc);
            Instant olderSource = Instant.parse("2026-09-01T00:00:00Z");
            Instant newerSource = Instant.parse("2026-09-02T00:00:00Z");
            for (String currency : Set.of("BTC", "USDT")) {
                writer.insertAccountSnapshot(snapshot(accountId, currency, "LIVE", olderSource, "1"));
                writer.insertAccountSnapshot(snapshot(accountId, currency, "SIM", newerSource, "2"));
                writer.insertAccountSnapshot(snapshot(accountId, currency, "LIVE", olderSource.minusSeconds(60), "3"));
            }
            JdbcAccountSnapshotReader reader = new JdbcAccountSnapshotReader(jdbc);
            assertTrue(reader.hasUnknownHistory(accountId));
            var live = reader.latest(accountId, "LIVE");
            assertEquals(2, live.size());
            assertTrue(live.stream().allMatch(s -> "LIVE".equals(s.tradeEnv())
                    && s.balance().compareTo(new BigDecimal("3")) == 0
                    && "NQ_MANAGED_ACCOUNT".equals(s.balanceScope())
                    && s.recordedAt() != null));
            assertEquals(2, reader.latest(accountId, "SIM").size());
            assertTrue(reader.latest(accountId, "SIM").stream()
                    .allMatch(s -> s.balance().compareTo(new BigDecimal("2")) == 0));
            assertThrows(IllegalStateException.class, () -> writer.assertAccountEnvironment(accountId, "LIVE"));
            assertThrows(IllegalArgumentException.class, () -> reader.latest(accountId, "UNKNOWN"));
            assertFalse(live.stream().anyMatch(s -> s.snapshotId() == legacyId));
            var accountView = new JdbcTradingQueryFacade(jdbc).queryAccount(accountId, "synthetic").orElseThrow();
            assertEquals(5, accountView.balances().size());
            assertEquals(1, accountView.balances().stream()
                    .filter(s -> s.tradeEnv() == null && s.balanceBasis() == null
                            && s.balanceScope() == null && s.recordedAt() == null).count());
            assertEquals(2, accountView.balances().stream()
                    .filter(s -> "LIVE".equals(s.tradeEnv()) && s.recordedAt() != null).count());
            assertEquals(2, accountView.balances().stream()
                    .filter(s -> "SIM".equals(s.tradeEnv()) && s.recordedAt() != null).count());

            insertTradeLedger(jdbc, accountId, "SIM", "10");
            insertTradeLedger(jdbc, accountId, "LIVE", "20");
            assertEquals(0, writer.currentBalance(accountId, "USDT", "LIVE").compareTo(BigDecimal.ZERO));
            assertEquals(0, writer.currentBalance(accountId, "USDT", "SIM").compareTo(BigDecimal.TEN));

            long freshAccount = jdbc.queryForObject("""
                    INSERT INTO accounts(account_code,venue,status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id
                    """, Long.class, "synthetic-" + UUID.randomUUID());
            long orphanAccount = jdbc.queryForObject("""
                    INSERT INTO accounts(account_code,venue,status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id
                    """, Long.class, "synthetic-" + UUID.randomUUID());
            jdbc.update("""
                    INSERT INTO positions(account_id,symbol,qty,available_qty,frozen_qty,avg_price,trace_id)
                    VALUES (?, 'BTC-USDT', 0.5, 0.5, 0, 10, 'legacy-unknown')
                    """, orphanAccount);
            assertThrows(IllegalStateException.class, () -> writer.assertAccountEnvironment(orphanAccount, "LIVE"));
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            long fundingAccount = jdbc.queryForObject("""
                    INSERT INTO accounts(account_code,venue,status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id
                    """, Long.class, "synthetic-" + UUID.randomUUID());
            transaction.execute(status -> new SimCashFundingService(writer).fundOnce(
                    fundingAccount, "synthetic-run", new BigDecimal("100"), "synthetic"));
            JdbcLedgerReconcileRepository reconcile = new JdbcLedgerReconcileRepository(jdbc);
            assertTrue(reconcile.findDiffs().stream().noneMatch(diff -> diff.accountId() == fundingAccount));
            jdbc.update("UPDATE account_snapshots SET balance=99 WHERE account_id=?", fundingAccount);
            assertTrue(reconcile.findDiffs().stream().anyMatch(diff -> diff.accountId() == fundingAccount
                    && "BALANCE_MISMATCH_SIM_LEDGER_CASH_PROJECTION".equals(diff.reason())
                    && diff.diffAmount().compareTo(BigDecimal.ONE) == 0));
            assertTrue(reconcile.findDiffs().stream().anyMatch(diff -> diff.accountId() == accountId
                    && "SNAPSHOT_PROVENANCE_UNKNOWN".equals(diff.reason())
                    && diff.diffAmount() == null));
            long positionAccount = jdbc.queryForObject("""
                    INSERT INTO accounts(account_code,venue,status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id
                    """, Long.class, "synthetic-" + UUID.randomUUID());
            jdbc.update("""
                    INSERT INTO positions(account_id,symbol,qty,available_qty,frozen_qty,avg_price,trace_id)
                    VALUES (?, 'BTC-USDT', 0.5, 0.5, 0, 10, 'synthetic')
                    """, positionAccount);
            writer.insertAccountSnapshot(new AccountSnapshotProjection(positionAccount, "BTC",
                    new BigDecimal("0.5"), new BigDecimal("0.5"), BigDecimal.ZERO,
                    olderSource, "synthetic", "LIVE", AccountBalanceBasis.POSITION_PROJECTION));
            assertTrue(reconcile.findDiffs().stream().noneMatch(diff -> diff.accountId() == positionAccount));
            jdbc.update("UPDATE positions SET qty=0.75 WHERE account_id=?", positionAccount);
            assertTrue(reconcile.findDiffs().stream().anyMatch(diff -> diff.accountId() == positionAccount
                    && "BALANCE_MISMATCH_LIVE_POSITION_PROJECTION".equals(diff.reason())
                    && diff.diffAmount().compareTo(new BigDecimal("0.25")) == 0));
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            CountDownLatch secondAcquired = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var first = pool.submit(() -> transaction.execute(status -> {
                    writer.lockSnapshotCurrencies(freshAccount, List.of("BTC"));
                    writer.assertAccountEnvironment(freshAccount, "LIVE");
                    writer.insertAccountSnapshot(snapshot(freshAccount, "BTC", "LIVE", olderSource, "1"));
                    firstLocked.countDown();
                    try {
                        if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                    return true;
                }));
                assertTrue(firstLocked.await(5, TimeUnit.SECONDS));
                var second = pool.submit(() -> transaction.execute(status -> {
                    secondStarted.countDown();
                    writer.lockSnapshotCurrencies(freshAccount, List.of("USDT"));
                    secondAcquired.countDown();
                    writer.assertAccountEnvironment(freshAccount, "SIM");
                    writer.insertAccountSnapshot(snapshot(freshAccount, "USDT", "SIM", newerSource, "2"));
                    return true;
                }));
                try {
                    assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                    assertFalse(secondAcquired.await(200, TimeUnit.MILLISECONDS));
                } finally {
                    releaseFirst.countDown();
                }
                assertTrue(first.get(5, TimeUnit.SECONDS));
                ExecutionException rejected = assertThrows(ExecutionException.class,
                        () -> second.get(5, TimeUnit.SECONDS));
                assertTrue(rejected.getCause() instanceof IllegalStateException);
                assertEquals(0, jdbc.queryForObject("""
                        SELECT count(*) FROM account_snapshots WHERE account_id=? AND trade_env='SIM'
                        """, Integer.class, freshAccount));
            }
        } finally {
            assertTrue(schema.matches("snapshot_semantics_[0-9a-f]{32}"));
            admin.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        }
    }

    private static AccountSnapshotProjection snapshot(long accountId, String currency, String env,
                                                       Instant source, String value) {
        BigDecimal amount = new BigDecimal(value);
        return new AccountSnapshotProjection(accountId, currency, amount, amount, BigDecimal.ZERO,
                source, "synthetic", env, AccountBalanceBasis.LEDGER_CASH_PROJECTION);
    }

    private static void insertTradeLedger(JdbcTemplate jdbc, long accountId, String env, String amount) {
        String orderId = UUID.randomUUID().toString();
        String tradeId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,
                    status,trace_id,venue,exchange_code,trade_env,exchange_order_id)
                VALUES (?,?,'BTC-USDT',?,'BUY','LIMIT',10,1,'FILLED','synthetic','OKX','OKX',?,?)
                """, orderId, accountId, "client-" + orderId, env, "exchange-" + orderId);
        jdbc.update("""
                INSERT INTO trades(trade_id,order_id,account_id,symbol,exchange,exchange_code,
                    exchange_trade_id,trade_env,price,qty,fee,fee_currency,trace_id,ts)
                VALUES (?,?,?,'BTC-USDT','OKX','OKX',?,?,10,1,0,'USDT','synthetic',?)
                """, tradeId, orderId, accountId, "fill-" + tradeId, env, Timestamp.from(Instant.now()));
        jdbc.update("""
                INSERT INTO ledger_entries(entry_id,account_id,currency,delta,balance_after,direction,
                    ref_type,ref_id,idempotency_key,trace_id,ts)
                VALUES (?,?, 'USDT', ?, ?, 'CREDIT', 'TRADE', ?, ?, 'synthetic', clock_timestamp())
                """, UUID.randomUUID().toString(), accountId, new BigDecimal(amount),
                new BigDecimal(amount), tradeId, tradeId + ":LEDGER:1");
        jdbc.update("""
                INSERT INTO ledger_entries(entry_id,account_id,currency,delta,balance_after,direction,
                    ref_type,ref_id,idempotency_key,trace_id,ts)
                VALUES (?,?, 'USDT', ?, 0, 'DEBIT', 'TRADE', ?, ?, 'synthetic', clock_timestamp())
                """, UUID.randomUUID().toString(), accountId, new BigDecimal(amount).negate(),
                tradeId, tradeId + ":LEDGER:2");
    }
}
