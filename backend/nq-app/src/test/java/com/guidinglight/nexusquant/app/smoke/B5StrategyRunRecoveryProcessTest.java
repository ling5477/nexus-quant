package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 独立 Spring JVM 消费同一 PG16/V49 的持久事实；控制器不修改运行中业务状态。 */
@EnabledIfSystemProperty(named="nq.b5.strategy", matches="true")
class B5StrategyRunRecoveryProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void durableRecoveryAndNegativeControls() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-strategy-recovery/" + UUID.randomUUID());
        System.out.println("B5_STRATEGY_RECOVERY_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            int run = 0;
            for (String row : List.of("RACE", "MAY", "ACTIVE", "LATE", "BEFORE_DROP", "AFTER_DROP")) {
                execute(pg, root.resolve(row), row, ++run);
            }
        }
    }

    private void execute(B0Processes.Pg pg, Path dir, String row, int run) throws Exception {
        Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", row).put("controllerPid", ProcessHandle.current().pid());
        boolean wire = row.endsWith("DROP");
        try {
            try (var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env);
                seed(fixture);
                var proxyEnv = B0Processes.cleanEnvironment(); proxyEnv.put("NQ_B4_DB", fixture.url());
                try (var proxy = new B0Processes.Child(B4PgWireProxyMain.class, dir, "wire", proxyEnv)) {
                    var bEnv = new LinkedHashMap<>(env);
                    String proxyPort = proxy.ready();
                    if (wire) bEnv.put("NQ_B0_DB", fixture.url().replaceFirst("127\\.0\\.0\\.1:[0-9]+", "127.0.0.1:" + proxyPort));
                    try (var reader = fixture.checker();
                         var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady();
                         var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", bEnv).awaitReady();
                         var c = new B0Processes.Child(B0NqProcessMain.class, dir, "c", env).awaitReady()) {
                        proof.put("database", fixture.name()).put("venuePid", venue.process.pid())
                                .put("nqPid", a.process.pid()).put("restartPid", b.process.pid()).put("successorPid", c.process.pid());
                        assertEquals(3, Set.of(a.process.pid(), b.process.pid(), c.process.pid()).size());
                        assertTrue(value(reader, "SHOW server_version").startsWith("16."));
                        assertEquals("50", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                        a.send(row.equals("MAY") ? "ARM_B5_POST_ARM" : "ARM_B5_PRE_SEND");
                        a.send("BEGIN_B5_STRATEGY");
                        awaitLog(a, "B5_CUT " + (row.equals("MAY") ? "POST_ARM" : "PRE_SEND"));
                        assertEquals("DISPATCHING", value(reader, "SELECT status FROM strategy_runs"));
                        proof.set("atCut", snapshot(reader));
                        if (row.equals("ACTIVE")) {
                            assertEquals("STRATEGY_RECOVERED 0", b.send("RECOVER_B5_STRATEGY"));
                            assertTrue(c.send("RUN_B5_STRATEGY").contains("strategy_run_active"));
                            assertEquals("DISPATCHING", value(reader, "SELECT status FROM strategy_runs"));
                            assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                            a.send("RELEASE_B5_PRE_SEND"); a.send("AWAIT_B5_STRATEGY");
                            assertEquals("RUNNING", value(reader, "SELECT status FROM strategy_runs"));
                        } else {
                            if (!row.equals("LATE")) a.kill();
                            b.send("B5_RECOVERY"); b.send("RECOVER");
                            if (row.equals("MAY")) {
                                assertEquals("MAY_HAVE_ESCAPED", value(reader, "SELECT state FROM ordinary_place_authorities"));
                                assertEquals("SENT", value(reader, "SELECT status FROM orders"));
                                assertTrue(b.send("RUN_B5_STRATEGY").contains("strategy_run_active"));
                                assertTrue(c.send("RUN_B5_STRATEGY").contains("strategy_run_active"));
                                assertEquals("DISPATCHING", value(reader, "SELECT status FROM strategy_runs"));
                            } else {
                                assertEquals("REVOKED_BEFORE_SEND", value(reader, "SELECT state FROM ordinary_place_authorities"));
                                assertEquals("CANCELLED", value(reader, "SELECT status FROM orders"));
                                assertEquals("4", value(reader, "SELECT version FROM orders"));
                                proof.set("afterOrderRecovery", snapshot(reader));
                                if (row.equals("RACE")) {
                                    try (var pool = Executors.newFixedThreadPool(2)) {
                                        var start = new CyclicBarrier(2);
                                        var rb = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return b.send("RECOVER_B5_STRATEGY"); });
                                        var rc = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return c.send("RECOVER_B5_STRATEGY"); });
                                        String first = rb.get(30, TimeUnit.SECONDS), second = rc.get(30, TimeUnit.SECONDS);
                                        assertEquals(Set.of("STRATEGY_RECOVERED 0", "STRATEGY_RECOVERED 1"), Set.of(first, second));
                                        proof.put("firstRecovery", first).put("secondRecovery", second);
                                    }
                                } else if (wire) {
                                    b.send("ARM_B5_STRATEGY_WIRE"); proxy.send("ARM " + row);
                                    String uncertain = b.send("RUN_B5_STRATEGY");
                                    awaitLog(proxy, "B4_WIRE_CUT " + row);
                                    assertTrue(uncertain.contains("outcome=FAILED"), uncertain);
                                    assertFalse(uncertain.contains("outcome=TRIGGERED"));
                                    awaitNoTransaction(reader);
                                    assertEquals(row.equals("AFTER_DROP") ? "FAILED" : "DISPATCHING", value(reader, "SELECT status FROM strategy_runs"));
                                    proof.set("afterUnknownCommit", snapshot(reader));
                                }
                                assertTrue(c.send("RUN_B5_STRATEGY").contains("dedup_hit"));
                                assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                                assertEquals("1", value(reader, "SELECT count(*) FROM b5_run_transitions WHERE new_status='FAILED'"));
                                if (row.equals("LATE")) {
                                    a.send("RELEASE_B5_PRE_SEND"); a.send("AWAIT_B5_STRATEGY");
                                    assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                                }
                                JsonNode stable = snapshot(reader);
                                assertEquals("STRATEGY_RECOVERED 0", c.send("RECOVER_B5_STRATEGY"));
                                assertTrue(c.send("RUN_B5_STRATEGY").contains(row.equals("LATE") ? "not_due" : "dedup_hit"));
                                assertEquals(stable, snapshot(reader));
                            }
                        }
                        // 新进程再次扫描：恢复重放不得再次完成，也不能给已消费窗口创建第二条 run/order。
                        c.close();
                        try (var restart = new B0Processes.Child(B0NqProcessMain.class, dir, "restart", env).awaitReady()) {
                            JsonNode stable = snapshot(reader);
                            String retry = restart.send("RUN_B5_STRATEGY");
                            String expected = row.equals("MAY") ? "strategy_run_active"
                                    : row.equals("ACTIVE") || row.equals("LATE") ? "not_due" : "dedup_hit";
                            assertTrue(retry.contains(expected), retry);
                            assertEquals(stable, snapshot(reader));
                        }
                        assertEquals("1", value(reader, "SELECT count(*) FROM strategy_runs"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM trades"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM execution_intents"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM execution_receipts"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                        assertEquals(row.equals("ACTIVE") ? 1 : 0, facts(endpoint).path("placeRequests").asInt());
                        assertEquals(0, facts(endpoint).path("cancels").asInt());
                        proof.set("final", snapshot(reader)); proof.set("venue", facts(endpoint));
                    }
                }
            }
            proof.put("result", "PASS").put("cleanup", true);
            System.out.println("B5_STRATEGY_RECOVERY_PASS " + row);
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Path raw = dir.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-SR", run);
        }
    }

    static void seed(B0Fixture fixture) throws Exception {
        try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
            s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot) "
                    + "SELECT 'b5-strategy','b5-strategy','B5 recovery fixture','TEST','oKx',account_id,'SIM',true,"
                    + "'{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"10\"}'::jsonb FROM accounts WHERE account_code='b0-account'");
            s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,enabled,dedup_scope,exchange_code,account_id,trade_env,created_at) "
                    + "SELECT 'b5','b5-strategy','0 0 0 1 1 *','UTC',true,'SCHEDULE_WINDOW','oKx',account_id,'SIM',date_trunc('year',CURRENT_TIMESTAMP)-INTERVAL '1 second' FROM accounts WHERE account_code='b0-account'");
            // 测试观察表只记实际状态改变，与业务 writer 同事务提交，不参与任何业务判断。
            s.execute("CREATE TABLE b5_run_transitions(old_status text,new_status text)");
            s.execute("CREATE FUNCTION b5_observe_run() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "IF NEW.status IS DISTINCT FROM OLD.status THEN INSERT INTO b5_run_transitions VALUES(OLD.status,NEW.status); END IF; RETURN NEW; END $$");
            s.execute("CREATE TRIGGER b5_observe_run AFTER UPDATE ON strategy_runs FOR EACH ROW EXECUTE FUNCTION b5_observe_run()");
            s.execute("GRANT INSERT,SELECT ON b5_run_transitions TO nq_b0_app; GRANT SELECT ON b5_run_transitions TO nq_b0_reader");
        }
    }

    private JsonNode facts(String endpoint) throws Exception {
        return mapper.readTree(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().send(
                HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
    }
    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : List.of("strategy_schedules", "strategy_runs", "orders", "ordinary_place_authorities", "b5_run_transitions"))
            result.set(table, mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM " + table + " t")));
        return result;
    }
    static String value(Connection reader, String sql) throws Exception {
        try (var s = reader.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
    private static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains(marker)) {
            assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < until, "missing boundary " + marker); Thread.sleep(20);
        }
    }
    private static void awaitNoTransaction(Connection reader) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!"0".equals(value(reader, "SELECT count(*) FROM pg_stat_activity WHERE usename='nq_b0_app' AND xact_start IS NOT NULL"))) {
            assertTrue(System.nanoTime() < until); Thread.sleep(20);
        }
    }
}
