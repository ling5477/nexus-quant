package com.guidinglight.nexusquant.app.smoke;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SQL 诊断只证明 effective 决定与 binding 的数据库原子性，不伪装成真实发送证明。 */
@EnabledIfSystemProperty(named = "nq.b5.quantity", matches = "true")
class B5EffectiveQuantityPostgresTest {
    @Test void projectionMustNotDeadlockOrderIdentityThenStatusWrite() throws Exception {
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg)) {
            fixture.initialize(true, "http://127.0.0.1:1", B0Processes.cleanEnvironment()); B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var writer = DriverManager.getConnection(fixture.url(), B0Fixture.APP, "");
                 var projector = DriverManager.getConnection(fixture.url(), B0Fixture.APP, "");
                 var observer = DriverManager.getConnection(fixture.url(), "postgres", "");
                 var pool = Executors.newSingleThreadExecutor()) {
                admit(writer); bind(fixture.url(), "10", new CyclicBarrier(1));
                writer.setAutoCommit(false);
                int writerPid = Integer.parseInt(value(writer, "SELECT pg_backend_pid()"));
                int projectorPid = Integer.parseInt(value(projector, "SELECT pg_backend_pid()"));
                value(writer, "UPDATE orders SET external_order_id='eq-external' WHERE order_id='eq-order' RETURNING order_id");
                var projection = pool.submit(() -> value(projector, "SELECT nq_project_strategy_run('eq-run')"));
                try {
                    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                    while (!value(observer, "SELECT " + writerPid + "=ANY(pg_blocking_pids(" + projectorPid + "))").equals("t")) {
                        assertTrue(System.nanoTime() < deadline, "projection must reach the held Order lock"); Thread.sleep(10);
                    }
                    // 同一 ACK 事务先补 identity 再更新状态，会重新执行 run 外键 KEY SHARE 检查。
                    value(writer, "UPDATE orders SET status='SENT',version=version+1 WHERE order_id='eq-order' RETURNING order_id");
                    writer.commit();
                    assertEquals("t", projection.get(10, TimeUnit.SECONDS));
                    assertEquals("RUNNING", value(observer, "SELECT status FROM strategy_runs"));
                    System.out.println("B5_EFFECTIVE_LOCK_PASS orderIdentityThenStatus=true projectionWaits=true noDeadlock=true");
                } finally { writer.rollback(); }
            }
        }
    }

    @Test void concurrentDifferentCalculationsReuseOneDurableWinner() throws Exception {
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg)) {
            fixture.initialize(true, "http://127.0.0.1:1", B0Processes.cleanEnvironment()); B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var app = DriverManager.getConnection(fixture.url(), B0Fixture.APP, ""); var reader = fixture.checker()) {
                admit(app);
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var barrier = new CyclicBarrier(2);
                    var a = pool.submit(() -> bind(fixture.url(), "10.001", barrier));
                    var b = pool.submit(() -> bind(fixture.url(), "10", barrier));
                    assertEquals(1, a.get(15, TimeUnit.SECONDS) + b.get(15, TimeUnit.SECONDS));
                }
                assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                assertEquals("t", value(reader, "SELECT o.qty=w.effective_quantity AND o.price=w.effective_price FROM orders o JOIN strategy_run_dispatch_work w USING(strategy_run_id)"));
                assertEquals("1", value(reader, "SELECT count(*) FROM audit_logs WHERE action='STRATEGY_EFFECTIVE_EXECUTION_BOUND'"));
                assertThrows(Exception.class, () -> value(app, "UPDATE strategy_run_dispatch_work SET effective_quantity=9 RETURNING effective_quantity"));
                assertThrows(Exception.class, () -> value(app, "UPDATE strategy_run_dispatch_work SET quantity=9 RETURNING quantity"));
                assertThrows(Exception.class, () -> value(app, "UPDATE orders SET qty=9 RETURNING qty"));
                System.out.println("B5_EFFECTIVE_PG_PASS concurrentWinner=1 order=1 authority=1 immutable=true");
            }
        }
    }

    @Test void effectiveDecisionCannotCommitWithoutOrderAndRejectionCannotRearm() throws Exception {
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg)) {
            fixture.initialize(true, "http://127.0.0.1:1", B0Processes.cleanEnvironment()); B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var app = DriverManager.getConnection(fixture.url(), B0Fixture.APP, ""); var reader = fixture.checker()) {
                admit(app);
                assertThrows(Exception.class, () -> value(app, "SELECT nq_bind_strategy_effective('eq-run',10,100,NULL)"));
                assertEquals("t", value(reader, "SELECT effective_quantity IS NULL FROM strategy_run_dispatch_work"));
                assertEquals("CREATED", value(reader, "SELECT status FROM strategy_runs"));
                assertThrows(Exception.class, () -> value(app, "SELECT nq_bind_strategy_effective('eq-run',0,100,NULL)"));
                assertThrows(Exception.class, () -> value(app, "SELECT nq_bind_strategy_effective('eq-run',10.000000001,100,NULL)"));
                assertEquals("t", value(app, "SELECT nq_bind_strategy_effective('eq-run',NULL,NULL,'OKX_QTY_BELOW_MIN_SIZE')"));
                assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                String finished = value(reader, "SELECT finished_at::text FROM strategy_runs");
                assertEquals("f", value(app, "SELECT nq_bind_strategy_effective('eq-run',10,100,NULL)"));
                assertEquals("f", value(app, "SELECT nq_project_strategy_run('eq-run')"));
                assertEquals(finished, value(reader, "SELECT finished_at::text FROM strategy_runs"));
                assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                assertEquals("0", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                System.out.println("B5_EFFECTIVE_PG_PASS partialCommitRejected=true invalidRejected=true rejectionAtomic=true replayNoop=true");
            }
        }
    }

    private static void admit(Connection app) throws Exception {
        assertEquals("eq-run", value(app, "SELECT nq_admit_strategy_work('eq-run',strategy_id,account_id,'OKX','SIM','MANUAL',"
                + "config_snapshot,'eq-request',now(),'eq-proof',NULL,NULL,version,'coid-eq-request','BTC-USDT','BUY','LIMIT',"
                + "10.0015,100.005,'GTC',NULL) FROM strategy_definitions WHERE strategy_id='b5-strategy'"));
    }
    private static int bind(String url, String quantity, CyclicBarrier barrier) throws Exception {
        try (var c = DriverManager.getConnection(url, B0Fixture.APP, "")) {
            c.setAutoCommit(false); barrier.await(5, TimeUnit.SECONDS);
            int won = value(c, "SELECT nq_bind_strategy_effective('eq-run'," + quantity + ",100,NULL)").equals("t") ? 1 : 0;
            if (value(c, "SELECT count(*) FROM orders WHERE strategy_run_id='eq-run'").equals("0")) {
                value(c, "SELECT nq_begin_strategy_dispatch('eq-run')");
                value(c, "SELECT nq_create_ordinary_place_order('eq-order',1,'eq-run','OKX','BTC-USDT','coid-eq-request','BUY',"
                        + "'LIMIT',effective_price,effective_quantity,'NEW','SQL_DIAGNOSTIC','eq-proof','SIM',0,now()) FROM strategy_run_dispatch_work WHERE strategy_run_id='eq-run'");
            }
            c.commit(); return won;
        }
    }
    private static String value(Connection c, String sql) throws Exception { return B5StrategyRunRecoveryProcessTest.value(c, sql); }
}
