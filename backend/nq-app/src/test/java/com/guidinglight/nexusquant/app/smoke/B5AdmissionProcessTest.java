package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 不修改运行中业务事实；使用原扫描、原发送及原恢复链验证新 admission 的直接交互。 */
@EnabledIfSystemProperty(named = "nq.b5.admission", matches = "true")
class B5AdmissionProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void staleDeathRestartAndRecoveryInteractions() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-admission-process/" + UUID.randomUUID());
        System.out.println("B5_ADMISSION_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            int run = 10;
            for (String row : List.of("STALE", "BEFORE_ADMISSION_DEATH", "AFTER_ADMISSION_DEATH", "RECOVERY_SCAN", "MAY")) {
                execute(pg, root.resolve(row), row, ++run);
            }
        }
    }

    private void execute(B0Processes.Pg pg, Path dir, String row, int run) throws Exception {
        Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", row).put("controllerPid", ProcessHandle.current().pid());
        try {
            try (var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env);
                B5StrategyRunRecoveryProcessTest.seed(fixture);
                try (var reader = fixture.checker();
                     var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady();
                     var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady()) {
                    proof.put("database", fixture.name()).put("venuePid", venue.process.pid())
                            .put("nqPid", a.process.pid()).put("restartPid", b.process.pid());
                    proof.put("postgres", value(reader, "SHOW server_version"));
                    assertEquals("50", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                    String arm = switch (row) {
                        case "STALE", "BEFORE_ADMISSION_DEATH" -> "ARM_B5_STRATEGY";
                        case "AFTER_ADMISSION_DEATH" -> "ARM_B5_ADMISSION";
                        case "MAY" -> "ARM_B5_POST_ARM";
                        default -> "ARM_B5_PRE_SEND";
                    };
                    a.send(arm); a.send("BEGIN_B5_STRATEGY");
                    String marker = switch (row) {
                        case "STALE", "BEFORE_ADMISSION_DEATH" -> "B5_STRATEGY_CUT";
                        case "AFTER_ADMISSION_DEATH" -> "B5_CUT AFTER_ADMISSION";
                        case "MAY" -> "B5_CUT POST_ARM";
                        default -> "B5_CUT PRE_SEND";
                    };
                    awaitLog(a, marker);
                    proof.set("atCut", snapshot(reader));
                    if (row.equals("STALE")) {
                        assertTrue(b.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                        proof.set("winnerBeforeStaleResume", snapshot(reader));
                        a.send("RELEASE_B5_STRATEGY");
                        assertTrue(a.send("AWAIT_B5_STRATEGY").contains("duplicate_admission"));
                        proof.put("staleAdmissionLoser", true);
                    } else {
                        a.kill();
                        if (row.equals("BEFORE_ADMISSION_DEATH")) {
                            assertEquals("0", value(reader, "SELECT count(*) FROM strategy_runs"));
                            assertTrue(b.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                        } else if (row.equals("RECOVERY_SCAN")) {
                            b.send("B5_RECOVERY"); b.send("RECOVER");
                            assertEquals("REVOKED_BEFORE_SEND", value(reader, "SELECT state FROM ordinary_place_authorities"));
                            proof.set("beforeRunRecovery", snapshot(reader));
                            try (var c = new B0Processes.Child(B0NqProcessMain.class, dir, "c", env).awaitReady();
                                 var pool = Executors.newFixedThreadPool(2)) {
                                proof.put("successorPid", c.process.pid());
                                var barrier = new CyclicBarrier(2);
                                var recovery = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return b.send("RECOVER_B5_STRATEGY"); });
                                var scan = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return c.send("RUN_B5_STRATEGY"); });
                                String count = recovery.get(30, TimeUnit.SECONDS);
                                String outcome = scan.get(30, TimeUnit.SECONDS);
                                assertTrue(count.endsWith(" 0") || count.endsWith(" 1"));
                                assertTrue(!outcome.contains("outcome=TRIGGERED") && !outcome.contains("outcome=FAILED"));
                                proof.put("explicitRecoveryCount", Integer.parseInt(count.substring(count.lastIndexOf(' ') + 1)));
                                proof.put("concurrentScanDispatched", false);
                                assertTrue(c.send("RUN_B5_STRATEGY").contains("dedup_hit"));
                            }
                            assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                            assertEquals("1", value(reader, "SELECT count(*) FROM b5_run_transitions WHERE new_status='FAILED'"));
                        } else {
                            b.send("B5_RECOVERY"); b.send("RECOVER");
                            assertTrue(b.send("RUN_B5_STRATEGY").contains("strategy_run_active"));
                            if (row.equals("AFTER_ADMISSION_DEATH")) {
                                assertEquals("CREATED", value(reader, "SELECT status FROM strategy_runs"));
                                assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                                proof.put("availabilityDisposition", "NO_ORDER_ADMITTED_RUN_FAIL_CLOSED_NOT_AUTOMATICALLY_RECOVERED");
                            } else {
                                assertEquals("MAY_HAVE_ESCAPED", value(reader, "SELECT state FROM ordinary_place_authorities"));
                                assertEquals("DISPATCHING", value(reader, "SELECT status FROM strategy_runs"));
                            }
                        }
                    }
                    boolean filled = row.equals("STALE") || row.equals("BEFORE_ADMISSION_DEATH");
                    if (filled) {
                        control(endpoint, "FILL 10 0.01"); b.send("RECOVER"); b.send("RECOVER");
                        assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                        assertEquals("4", value(reader, "SELECT version FROM orders"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                        assertEquals("4", value(reader, "SELECT count(*) FROM ledger_entries"));
                    }
                    // 新 JVM 的 busy set 是空的，但持久窗口仍已消费；两次真正重启均不能建立替代run。
                    b.close();
                    JsonNode stable = snapshot(reader);
                    for (int restart = 0; restart < 2; restart++) {
                        try (var next = new B0Processes.Child(B0NqProcessMain.class, dir, "restart-" + restart, env).awaitReady()) {
                            proof.put("restart" + restart + "Pid", next.process.pid());
                            String scan = next.send("RUN_B5_STRATEGY");
                            assertTrue(!scan.contains("outcome=TRIGGERED") && !scan.contains("outcome=FAILED"));
                            assertEquals(stable, snapshot(reader));
                        }
                    }
                    assertEquals("1", value(reader, "SELECT count(*) FROM strategy_runs"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM strategy_runs WHERE admission_schedule_id='b5'"));
                    int orders = row.equals("AFTER_ADMISSION_DEATH") ? 0 : 1;
                    assertEquals(Integer.toString(orders), value(reader, "SELECT count(*) FROM orders"));
                    assertEquals(Integer.toString(orders), value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                    assertEquals(filled ? 1 : 0, facts(endpoint).path("placeRequests").asInt());
                    assertEquals(0, facts(endpoint).path("cancels").asInt());
                    if (!filled) {
                        assertEquals("0", value(reader, "SELECT count(*) FROM trades"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                    }
                    proof.set("final", snapshot(reader)); proof.set("venue", facts(endpoint));
                }
            }
            proof.put("result", "PASS");
            System.out.println("B5_ADMISSION_PASS " + row);
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Path raw = dir.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-AD", run);
        }
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : List.of("strategy_schedules", "strategy_runs", "orders", "ordinary_place_authorities",
                "b5_run_transitions", "trades", "ledger_entries", "event_store", "execution_intents", "execution_receipts")) {
            result.set(table, mapper.readTree(value(reader,
                    "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        }
        return result;
    }
    private JsonNode facts(String endpoint) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        }
    }
    private void control(String endpoint, String command) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(endpoint + "/control"))
                    .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(command)).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
    private static String value(Connection reader, String sql) throws Exception {
        return B5StrategyRunRecoveryProcessTest.value(reader, sql);
    }
    private static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains(marker)) {
            assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < deadline, marker); Thread.sleep(20);
        }
    }
}
