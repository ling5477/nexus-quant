package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 只补充最终组合证明；状态、成交与账务均由真实业务进程产生。 */
@EnabledIfSystemProperty(named = "nq.b5.final", matches = "true")
class B5FinalQualificationInteractionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void remainingInteractionsStopAtFirstFailure() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-final-interactions/" + UUID.randomUUID());
        System.out.println("B5_FINAL_ROOT " + root);
        int index = 0;
        for (String row : List.of("PRE_ADMISSION_DEATH", "CREATED_DEATH_SCAN", "CREATED_PAUSED_SCAN", "TERMINAL_SCAN")) {
            for (int repeat = 1; repeat <= 3; repeat++) execute(root.resolve(row + "-" + repeat), row, ++index);
        }
        execute(root.resolve("ORDINARY_RESTART"), "ORDINARY_RESTART", ++index);
    }

    private void execute(Path dir, String row, int index) throws Exception {
        Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", row)
                .put("controllerPid", ProcessHandle.current().pid());
        try (var pg = B0Processes.Pg.start()) {
            String database;
            try (var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                database = fixture.name();
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env);
                B5StrategyRunRecoveryProcessTest.seed(fixture);
                // 仅启动前设置非整数步长输入；年度窗口确保并发 scanner 针对同一 dueAt。
                try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
                    s.execute("UPDATE strategy_definitions SET config_snapshot=jsonb_set(jsonb_set(config_snapshot,'{quantity}','\"10.0005\"'),'{price}','\"100.005\"')");
                }
                try (var reader = fixture.checker();
                     var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady();
                     var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady();
                     var c = new B0Processes.Child(B0NqProcessMain.class, dir, "c", env).awaitReady()) {
                    proof.put("database", database).put("nqPid", a.process.pid()).put("restartPid", b.process.pid())
                            .put("successorPid", c.process.pid()).put("venuePid", venue.process.pid());
                    proof.put("postgresVersion", value(reader, "SHOW server_version"));
                    assertTrue(proof.path("postgresVersion").asText().startsWith("16."));
                    assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                    proof.put("schemaVersion", "51");
                    boolean ordinary = row.equals("ORDINARY_RESTART");
                    if (ordinary) {
                        assertTrue(a.send("PLACE_B2").endsWith("ACCEPTED"));
                        proof.set("atCut", snapshot(reader));
                        a.kill(); b.send("PLACE_B2");
                    } else if (row.equals("TERMINAL_SCAN")) {
                        assertTrue(a.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                        B5EffectiveQuantityProcessTest.control(endpoint, "FILL 10 0.01");
                        a.send("RECOVER"); a.send("RECOVER");
                        assertEquals("RUNNING", value(reader, "SELECT status FROM strategy_runs"));
                        assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                        proof.set("atCut", snapshot(reader)); a.kill();
                    } else {
                        boolean before = row.equals("PRE_ADMISSION_DEATH");
                        a.send(before ? "ARM_B5_STRATEGY" : "ARM_B5_ADMISSION"); a.send("BEGIN_B5_STRATEGY");
                        B5EffectiveQuantityProcessTest.awaitLog(a, before ? "B5_STRATEGY_CUT" : "B5_CUT AFTER_ADMISSION");
                        assertEquals(before ? "0" : "1", value(reader, "SELECT count(*) FROM strategy_runs"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                        proof.set("atCut", snapshot(reader));
                        if (!row.equals("CREATED_PAUSED_SCAN")) a.kill();
                    }
                    // 两个独立 JVM 同时进入真实 recovery 与 scanner；不替换任何查询或门禁结果。
                    if (!ordinary) {
                        try (var pool = Executors.newFixedThreadPool(2)) {
                            var start = new CyclicBarrier(2);
                            var first = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return b.send("RECOVER_V51_ALL"); });
                            var second = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return c.send("RUN_B5_STRATEGY"); });
                            proof.put("bResult", first.get(30, TimeUnit.SECONDS));
                            proof.put("cResult", second.get(30, TimeUnit.SECONDS));
                        }
                        proof.set("afterSuccessors", snapshot(reader));
                        if (a.process.isAlive()) {
                            a.send("RELEASE_B5_PRE_SEND"); proof.put("oldOwnerResult", a.send("AWAIT_B5_STRATEGY"));
                        }
                    }
                    if (!row.equals("TERMINAL_SCAN")) B5EffectiveQuantityProcessTest.control(endpoint, "FILL 10 0.01");
                    b.send("RECOVER"); c.send("RECOVER"); b.send("RECOVER_V51_ALL"); c.send("RECOVER_V51_ALL");
                    proof.set("final", snapshot(reader)); proof.set("venue", facts(endpoint));
                    assertAccounting(reader, endpoint);
                    if (!ordinary) {
                        assertEquals("1", value(reader, "SELECT count(*) FROM strategy_runs"));
                        assertEquals("SUCCEEDED", value(reader, "SELECT status FROM strategy_runs"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM orders o JOIN strategy_runs r ON r.strategy_run_id=o.strategy_run_id"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM b5_run_transitions WHERE new_status='SUCCEEDED'"));
                        B5EffectiveQuantityProcessTest.assertDecimal("10.0005", value(reader, "SELECT quantity FROM strategy_run_dispatch_work"));
                        B5EffectiveQuantityProcessTest.assertDecimal("10", value(reader, "SELECT effective_quantity FROM strategy_run_dispatch_work"));
                        B5EffectiveQuantityProcessTest.assertDecimal("100", value(reader, "SELECT effective_price FROM strategy_run_dispatch_work"));
                        assertFalse(c.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                    }
                    var stable = snapshot(reader);
                    b.send("RECOVER"); c.send("RECOVER_V51_ALL");
                    assertEquals(stable, snapshot(reader));
                    // 新进程重放相同入口，不能生成替代 run、Order 或账务。
                    b.kill(); c.kill();
                    try (var d = new B0Processes.Child(B0NqProcessMain.class, dir, "d", env).awaitReady()) {
                        proof.put("replayPid", d.process.pid());
                        proof.put("replayResult", d.send(ordinary ? "PLACE_B2" : "RUN_B5_STRATEGY"));
                        d.send("RECOVER"); d.send("RECOVER_V51_ALL");
                        assertEquals(stable, snapshot(reader)); assertAccounting(reader, endpoint);
                        proof.set("afterReplay", snapshot(reader)); proof.set("venue", facts(endpoint));
                    }
                }
            }
            assertTrue(pg.databaseAbsent(database));
            proof.put("databaseAbsent", true);
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.toString()); throw failure;
        } finally {
            if (!proof.has("result")) proof.put("result", "PASS");
            Path raw = dir.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-FINAL", index);
        }
        System.out.println("B5_FINAL_PASS " + row + " " + index);
    }

    private void assertAccounting(Connection reader, String endpoint) throws Exception {
        assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
        assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
        assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
        assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
        assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
        assertEquals("4", value(reader, "SELECT count(*) FROM ledger_entries"));
        B5EffectiveQuantityProcessTest.assertDecimal("10", value(reader, "SELECT sum(qty) FROM trades"));
        B5EffectiveQuantityProcessTest.assertDecimal("10", facts(endpoint).path("order").path("sz").asText());
        B5EffectiveQuantityProcessTest.assertDecimal("100", facts(endpoint).path("order").path("px").asText());
        assertEquals(1, facts(endpoint).path("placeRequests").asInt());
        assertEquals(0, facts(endpoint).path("cancels").asInt());
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : List.of("strategy_runs", "strategy_run_dispatch_work", "strategy_schedules", "orders",
                "ordinary_place_authorities", "trades", "ledger_entries", "b5_run_transitions")) {
            result.set(table, mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        }
        result.set("tradeExecuted", mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM event_store t WHERE event_type='TradeExecuted'")));
        return result;
    }

    private JsonNode facts(String endpoint) throws Exception { return B5EffectiveQuantityProcessTest.venueFacts(endpoint, mapper); }
    private static String value(Connection reader, String sql) throws Exception { return B5StrategyRunRecoveryProcessTest.value(reader, sql); }
}
