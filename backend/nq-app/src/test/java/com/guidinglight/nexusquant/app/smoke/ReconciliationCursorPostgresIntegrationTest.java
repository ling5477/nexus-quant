package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 真实 PG16 独立 schema 验证进度持久性与锁边界；所有实例使用 production 仓储及 Spring 事务代理。 */
@EnabledIfSystemProperty(named = "nq.l4.blockers.enabled", matches = "true")
class ReconciliationCursorPostgresIntegrationTest {
    private static final List<OrderStatus> STATES = List.of(OrderStatus.FILLED, OrderStatus.CANCELLED);

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void freshAndV47UpgradePreserveHistoryAndOnlyAddCursor(boolean upgrade) {
        try (var f = new Fixture(false)) {
            var previous = f.flyway("47");
            List<java.util.Map<String, Object>> history = List.of();
            long tablesBefore = 0;
            if (upgrade) {
                assertEquals(47, previous.migrate().migrationsExecuted);
                history = f.jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
                tablesBefore = f.tableCount();
                f.account(); f.add("old", "OKX", OrderStatus.CANCELLED, 0);
            }
            assertEquals(upgrade ? 1 : 48, f.latest.migrate().migrationsExecuted);
            f.latest.validate();
            assertEquals("48", f.latest.info().current().getVersion().getVersion());
            assertEquals(0, f.latest.info().pending().length);
            assertEquals(48, f.jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class));
            if (upgrade) {
                assertEquals(history, f.jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IS DISTINCT FROM '48' ORDER BY installed_rank"));
                assertEquals(tablesBefore + 1, f.tableCount());
                assertEquals(0, f.raw.findByOrderId("old").orElseThrow().version());
            }
            assertEquals(5, f.jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='reconciliation_scan_cursors'", Integer.class));
            assertEquals(1, f.jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND tablename='reconciliation_scan_cursors'", Integer.class));
            assertEquals(5, f.jdbc.queryForObject("SELECT count(*) FROM pg_attribute WHERE attrelid='reconciliation_scan_cursors'::regclass AND attnum>0 AND col_description(attrelid,attnum) IS NOT NULL", Integer.class));
            assertNotNull(f.jdbc.queryForObject("SELECT obj_description('reconciliation_scan_cursors'::regclass)", String.class));
            var checksum = f.jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='48'", Integer.class);
            assertEquals(0, f.latest.migrate().migrationsExecuted);
            f.latest.validate();
            assertEquals(checksum, f.jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='48'", Integer.class));
            f.jdbc.update("INSERT INTO reconciliation_scan_cursors(venue) VALUES ('OKX')");
            assertThrows(DataAccessException.class, () -> f.jdbc.update("INSERT INTO reconciliation_scan_cursors(venue) VALUES ('OKX')"));
            assertThrows(DataAccessException.class, () -> f.jdbc.update("UPDATE reconciliation_scan_cursors SET cursor_order_id='half-key'"));
            assertThrows(DataAccessException.class, () -> f.jdbc.update("UPDATE reconciliation_scan_cursors SET revision=-1"));
            assertThrows(DataAccessException.class, () -> f.jdbc.update("INSERT INTO reconciliation_scan_cursors(venue) VALUES (' ')"));
            System.out.println("C2_FLYWAY_PASS upgrade=" + upgrade + " latest=48 checksum=" + checksum + " tables=" + f.tableCount());
        }
    }

    @Test void tiesWrapVenueAndChangingEligibilityRemainReachable() {
        try (var f = new Fixture(true); var app = f.instance()) {
            var scan = app.getBean(OrderRepository.class);
            f.add("other", "BINANCE", OrderStatus.CANCELLED, -100);
            f.add("a", "OKX", OrderStatus.FILLED, 0);
            f.add("b", "OKX", OrderStatus.CANCELLED, 0);
            f.add("c", "OKX", OrderStatus.CANCELLED, 0);
            assertEquals(List.of("a", "b"), ids(scan, "OKX", 2));
            assertEquals(List.of("c", "a"), ids(scan, "OKX", 2));
            // 在游标两侧插入候选并切换 eligibility，不能通过排序键相同或状态变化永久丢失。
            f.add("behind", "OKX", OrderStatus.CANCELLED, -1);
            f.add("ahead", "OKX", OrderStatus.REJECTED, 1);
            f.jdbc.update("UPDATE orders SET status='REJECTED' WHERE order_id='b'");
            assertEquals(List.of("c", "behind"), ids(scan, "OKX", 2));
            f.jdbc.update("UPDATE orders SET status='CANCELLED' WHERE order_id IN ('b','ahead')");
            assertEquals(List.of("a", "b"), ids(scan, "OKX", 2));
            assertEquals(List.of("c", "ahead"), ids(scan, "OKX", 2));
            f.jdbc.update("DELETE FROM orders WHERE order_id='ahead'");
            assertEquals(List.of("behind"), ids(scan, "OKX", 1));
            assertEquals(List.of("other"), ids(scan, "BINANCE", 1));
            assertEquals(6, f.revision("OKX"));
            assertEquals(1, f.revision("BINANCE"));
            System.out.println("C2_DYNAMIC_PASS ties/wrap/venue/new/eligible/ineligible/deletedCursor");
        }
    }

    @Test void destroyedInstanceContinuesDatabaseProgressAndCrashedBatchReturnsNextCircle() {
        try (var f = new Fixture(true)) {
            f.add("a", "OKX", OrderStatus.FILLED, 0);
            f.add("b", "OKX", OrderStatus.CANCELLED, 1);
            try (var first = f.instance()) {
                assertEquals(List.of("a"), ids(first.getBean(OrderRepository.class), "OKX", 1));
            }
            // 销毁全部 scanner/Spring 事务实例；不处理 a，模拟预留后崩溃。
            try (var second = f.instance()) {
                assertEquals(List.of("b"), ids(second.getBean(OrderRepository.class), "OKX", 1));
                assertEquals(List.of("a"), ids(second.getBean(OrderRepository.class), "OKX", 1));
                assertEquals(3, f.revision("OKX"));
            }
            System.out.println("C2_RESTART_PASS first=a destroyed=true second=b crashBatchRevisited=a");
        }
    }

    @Test void twoIndependentInstancesSerializeInitialCreationAndFortyReservations() throws Exception {
        try (var f = new Fixture(true); var first = f.instance(); var second = f.instance()) {
            for (int i = 0; i < 41; i++) f.add("order-%02d".formatted(i), "OKX", OrderStatus.CANCELLED, i);
            var a = first.getBean(OrderRepository.class);
            var b = second.getBean(OrderRepository.class);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var all = new java.util.HashSet<String>();
                for (int round = 0; round < 20; round++) {
                    var ready = new CountDownLatch(2);
                    var release = new CountDownLatch(1);
                    var x = pool.submit(() -> { ready.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return ids(a, "OKX", 1); });
                    var y = pool.submit(() -> { ready.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return ids(b, "OKX", 1); });
                    assertTrue(ready.await(10, TimeUnit.SECONDS)); release.countDown();
                    var xs = x.get(15, TimeUnit.SECONDS); var ys = y.get(15, TimeUnit.SECONDS);
                    assertEquals(1, xs.size()); assertEquals(1, ys.size());
                    assertTrue(all.add(xs.getFirst())); assertTrue(all.add(ys.getFirst()));
                    assertEquals((round + 1) * 2L, f.revision("OKX"));
                }
                assertEquals(40, all.size());
                assertEquals(List.of("order-40"), ids(a, "OKX", 1));
                assertEquals(List.of("order-00"), ids(b, "OKX", 1));
                assertEquals(42, f.revision("OKX"));
                System.out.println("C2_CONCURRENCY_PASS instances=2 concurrentReservations=40 lostUpdates=0 wrapRevision=42");
            } finally {
                pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
            }
        }
    }

    @Test void reservationCommitsOutsideCallerAndFailuresRollbackProgress() {
        try (var f = new Fixture(true); var app = f.instance()) {
            f.add("a", "OKX", OrderStatus.CANCELLED, 0);
            f.add("b", "OKX", OrderStatus.CANCELLED, 1);
            var scan = app.getBean(OrderRepository.class);
            var outer = new TransactionTemplate(app.getBean(DataSourceTransactionManager.class));
            assertThrows(IllegalStateException.class, () -> ids(f.raw, "OKX", 1));
            outer.executeWithoutResult(tx -> {
                assertEquals(List.of("a"), ids(scan, "OKX", 1));
                // 独立连接立即取得 cursor lock，证明返回时已释放；外层回滚不撤销进度。
                assertEquals(1, f.jdbc.queryForObject("SELECT revision FROM reconciliation_scan_cursors WHERE venue='OKX' FOR UPDATE NOWAIT", Long.class));
                tx.setRollbackOnly();
            });
            assertEquals(1, f.revision("OKX"));
            f.jdbc.execute("CREATE FUNCTION reject_cursor_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture cursor failure'; END $$");
            f.jdbc.execute("CREATE TRIGGER reject_cursor BEFORE UPDATE ON reconciliation_scan_cursors FOR EACH ROW EXECUTE FUNCTION reject_cursor_update()");
            assertThrows(DataAccessException.class, () -> ids(scan, "OKX", 1));
            assertEquals(1, f.revision("OKX"));
            f.jdbc.execute("DROP TRIGGER reject_cursor ON reconciliation_scan_cursors");
            assertEquals(List.of("b"), ids(scan, "OKX", 1));
            assertEquals(2, f.revision("OKX"));
            f.jdbc.update("UPDATE orders SET status='REJECTED'");
            assertTrue(ids(scan, "OKX", 100).isEmpty()); assertEquals(2, f.revision("OKX"));
            assertThrows(IllegalArgumentException.class, () -> ids(scan, "OKX", 0));
            assertThrows(IllegalArgumentException.class, () -> ids(scan, "OKX", -1));
            assertThrows(IllegalArgumentException.class, () -> ids(scan, " ", 1));
            assertThrows(IllegalArgumentException.class, () -> scan.reserveReconciliationCandidates("OKX", List.of(), 1));
            assertEquals(2, f.revision("OKX"));
            System.out.println("C2_TRANSACTION_PASS requiresNew/lockRelease/failureRollback/empty/invalid");
        }
    }

    private static List<String> ids(OrderRepository scan, String venue, int limit) {
        var selected = scan.reserveReconciliationCandidates(venue, STATES, limit);
        assertTrue(selected.size() <= limit);
        assertEquals(selected.size(), selected.stream().map(OrderRecord::orderId).distinct().count());
        return selected.stream().map(OrderRecord::orderId).toList();
    }

    @Configuration @EnableTransactionManagement
    static class Transactions { }

    private static final class Fixture implements AutoCloseable {
        final String url = System.getProperty("spring.datasource.url", "");
        final String schema = "c2_scan_" + UUID.randomUUID().toString().replace("-", "");
        final JdbcTemplate jdbc;
        final JdbcOrderRepository raw;
        final Flyway latest;

        Fixture(boolean migrate) {
            assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.endsWith("/nq_l4_blocker"));
            jdbc = new JdbcTemplate(source()); raw = new JdbcOrderRepository(jdbc); latest = flyway("48");
            if (migrate) { latest.migrate(); latest.validate(); account(); }
        }

        DriverManagerDataSource source() { return new DriverManagerDataSource(url + "?currentSchema=" + schema, "postgres", ""); }
        Flyway flyway(String target) {
            return Flyway.configure().dataSource(url, "postgres", "").locations("classpath:db/migration")
                    .schemas(schema).defaultSchema(schema).createSchemas(true).cleanDisabled(false).target(target).load();
        }
        AnnotationConfigApplicationContext instance() {
            var context = new AnnotationConfigApplicationContext();
            var dataSource = source();
            context.register(Transactions.class);
            context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
            context.registerBean("transactionManager", DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
            context.registerBean(JdbcOrderRepository.class);
            context.refresh(); return context;
        }
        void account() { jdbc.update("INSERT INTO accounts(account_code,venue,status) VALUES ('scan-fixture','OKX','ACTIVE')"); }
        void add(String id, String venue, OrderStatus status, int seconds) {
            raw.insert(new OrderRecord(id, 1L, null, venue, "BTC-USDT", id, "BUY", "LIMIT", BigDecimal.ONE,
                    BigDecimal.ONE, null, status, "SCAN_FIXTURE", "scan-fixture", "SIM"), Instant.EPOCH.plusSeconds(seconds));
        }
        long revision(String venue) { return jdbc.queryForObject("SELECT revision FROM reconciliation_scan_cursors WHERE venue=?", Long.class, venue); }
        long tableCount() { return jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE'", Long.class); }
        @Override public void close() { latest.clean(); }
    }
}
