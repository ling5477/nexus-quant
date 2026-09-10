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

/** 同一持久 run 的真实进程交叠；控制器不写运行中的 Order/run/authority/cursor 事实。 */
@EnabledIfSystemProperty(named = "nq.b5.v51", matches = "true")
class B5V51RecoveryProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void staleScannerAndRestartKeepConsumedWindow() throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/b5-v51-stale/" + UUID.randomUUID()); Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", "STALE_SCANNER_RESTART");
        System.out.println("B5_V51_STALE_ROOT " + dir);
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env); B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var reader = fixture.checker();
                 var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady();
                 var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady()) {
                a.send("ARM_B5_STRATEGY"); a.send("BEGIN_B5_STRATEGY"); awaitLog(a, "B5_STRATEGY_CUT");
                assertTrue(b.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                a.send("RELEASE_B5_STRATEGY"); assertTrue(a.send("AWAIT_B5_STRATEGY").contains("duplicate_admission"));
                control(endpoint, "FILL 10 0.01"); b.send("RECOVER"); b.send("RECOVER_V51_ALL");
                assertEquals("SUCCEEDED", value(reader, "SELECT status FROM strategy_runs"));
                ObjectNode stable = snapshot(reader); a.kill(); b.kill();
                try (var c = new B0Processes.Child(B0NqProcessMain.class, dir, "c", env).awaitReady()) {
                    assertTrue(c.send("RUN_B5_STRATEGY").contains("not_due"));
                    assertEquals(stable, snapshot(reader));
                    assertTrue(c.send("RUN_B5_STRATEGY").contains("not_due"));
                    assertEquals(stable, snapshot(reader));
                    assertEquals("1", value(reader, "SELECT count(*) FROM strategy_runs"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                    assertEquals(1, facts(endpoint).path("placeRequests").asInt());
                    proof.put("database", fixture.name()).put("nqPid", a.process.pid()).put("restartPid", b.process.pid())
                            .put("successorPid", c.process.pid()).put("venuePid", venue.process.pid()).put("result", "PASS");
                    proof.set("final", stable); proof.set("venue", facts(endpoint));
                }
            }
        } catch (Exception | AssertionError failure) { proof.put("result", "FAIL"); throw failure; }
        finally {
            Path raw = dir.resolve("raw-proof.json"); Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-V51S", 1);
        }
    }

    @Test void pausedOwnerSuccessorsTerminalAndNoResendMatrix() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-v51-process/" + UUID.randomUUID());
        System.out.println("B5_V51_PROCESS_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            int run = 0;
            for (String row : List.of("CREATED_PAUSE", "CREATED_TWO", "B_PAUSE", "B_DEATH", "REVOKED_PAUSE",
                    "MAY_PAUSE", "MAY_DEATH", "TERMINAL_TWO", "CANCEL_ZERO", "CANCEL_PARTIAL", "KILL_CREATED",
                    "RECOVERY_SCAN", "B_ROLLBACK")) {
                run++;
                String selection = System.getProperty("nq.b5.v51.rows", "ALL");
                if (selection.equals("ALL") || List.of(selection.split(",")).contains(row)) execute(pg, root.resolve(row), row, run);
            }
        }
    }

    private void execute(B0Processes.Pg pg, Path dir, String row, int run) throws Exception {
        Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", row).put("controllerPid", ProcessHandle.current().pid());
        try (var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env, row.equals("KILL_CREATED"));
            B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var reader = fixture.checker();
                 var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady();
                 var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady();
                 var c = new B0Processes.Child(B0NqProcessMain.class, dir, "c", env).awaitReady()) {
                proof.put("database", fixture.name()).put("nqPid", a.process.pid()).put("restartPid", b.process.pid())
                        .put("successorPid", c.process.pid()).put("venuePid", venue.process.pid());
                assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                boolean terminal = row.startsWith("CANCEL_") || row.equals("TERMINAL_TWO");
                boolean rollback = row.equals("B_ROLLBACK");
                boolean may = row.startsWith("MAY_");
                boolean prepared = row.startsWith("B_") || row.equals("REVOKED_PAUSE");
                if (terminal) {
                    assertTrue(a.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                } else if (rollback) {
                    a.send("ARM_V51_TX B ROLLBACK");
                    assertTrue(a.send("RUN_B5_STRATEGY").contains("outcome=FAILED"));
                    assertEquals("CREATED", value(reader, "SELECT status FROM strategy_runs"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                } else {
                    a.send(may ? "ARM_B5_POST_ARM" : prepared ? "ARM_V51_B" : "ARM_B5_ADMISSION");
                    a.send("BEGIN_B5_STRATEGY");
                    awaitLog(a, "B5_CUT " + (may ? "POST_ARM" : prepared ? "V51_B" : "AFTER_ADMISSION"));
                    assertEquals(prepared || may ? "DISPATCHING" : "CREATED", value(reader, "SELECT status FROM strategy_runs"));
                }
                proof.set("atCut", snapshot(reader));
                String originalRun = value(reader, "SELECT strategy_run_id FROM strategy_runs");
                if (terminal || row.endsWith("DEATH") || row.equals("CREATED_TWO") || rollback) a.kill();
                if (row.equals("KILL_CREATED")) b.send("ENGAGE");
                if (row.equals("REVOKED_PAUSE")) {
                    b.send("B5_RECOVERY");
                    assertEquals("REVOKED_BEFORE_SEND", value(reader, "SELECT state FROM ordinary_place_authorities"));
                }
                if (terminal) {
                    if (!row.equals("CANCEL_ZERO")) control(endpoint, row.equals("CANCEL_PARTIAL") ? "FILL 4 0.01" : "FILL 10 0.01");
                    b.send("RECOVER");
                    if (row.startsWith("CANCEL_")) {
                        b.send("CANCEL_V51");
                        b.send("RECOVER_V51_ALL");
                        assertEquals("RUNNING", value(reader, "SELECT status FROM strategy_runs"), "cancel ACK is not finality");
                        control(endpoint, "CANCEL_EFFECT"); b.send("RECOVER");
                    }
                }
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var start = new CyclicBarrier(2);
                    var x = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return b.send("RECOVER_V51_ALL"); });
                    var y = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return c.send(row.equals("RECOVERY_SCAN") ? "RUN_B5_STRATEGY" : "RECOVER_V51_ALL"); });
                    proof.put("bResult", x.get(30, TimeUnit.SECONDS)); proof.put("cResult", y.get(30, TimeUnit.SECONDS));
                }
                proof.set("beforeOldOwner", snapshot(reader));
                if (a.process.isAlive()) {
                    a.send("RELEASE_B5_PRE_SEND"); a.send("AWAIT_B5_STRATEGY");
                }
                if (may && row.endsWith("DEATH")) {
                    b.send("B5_RECOVERY"); b.send("RECOVER"); b.send("RECOVER_V51_ALL");
                    assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                    assertEquals("MAY_HAVE_ESCAPED", value(reader, "SELECT state FROM ordinary_place_authorities"));
                    assertEquals("RUNNING", value(reader, "SELECT status FROM strategy_runs"));
                    proof.put("disposition", "CORRECTNESS_REQUIRED_UNRESOLVED");
                } else if (row.equals("REVOKED_PAUSE") || row.equals("KILL_CREATED")) {
                    assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                    assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                } else if (row.startsWith("CANCEL_")) {
                    assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_order_cancel_finality"));
                    assertEquals(row.equals("CANCEL_ZERO") ? "0.00000000" : "4.00000000",
                            value(reader, "SELECT executed_quantity FROM ordinary_order_cancel_finality"));
                } else {
                    if (!terminal) { control(endpoint, "FILL 10 0.01"); b.send("RECOVER"); b.send("RECOVER"); }
                    b.send("RECOVER_V51_ALL"); c.send("RECOVER_V51_ALL");
                    assertEquals("SUCCEEDED", value(reader, "SELECT status FROM strategy_runs"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM b5_run_transitions WHERE new_status='SUCCEEDED'"));
                }
                JsonNode stable = snapshot(reader);
                b.send("RECOVER_V51_ALL"); c.send("RECOVER_V51_ALL");
                assertEquals(stable, snapshot(reader));
                assertEquals(originalRun, value(reader, "SELECT strategy_run_id FROM strategy_runs"));
                assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                assertTrue(facts(endpoint).path("placeRequests").asInt() <= 1, "SAME_RUN_DUPLICATE_PLACE");
                proof.set("final", snapshot(reader)); proof.set("venue", facts(endpoint)); proof.put("result", "PASS");
                System.out.println("B5_V51_PROCESS_PASS " + row);
            }
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Path raw = dir.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-V51", run);
        }
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode facts = mapper.createObjectNode();
        for (String table : List.of("strategy_runs", "strategy_run_dispatch_work", "strategy_schedules", "orders",
                "ordinary_place_authorities", "ordinary_order_cancel_finality", "trades", "b5_run_transitions")) {
            facts.set(table, mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        }
        return facts;
    }

    private JsonNode facts(String endpoint) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        }
    }

    private void control(String endpoint, String command) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    private static String value(Connection reader, String sql) throws Exception { return B5StrategyRunRecoveryProcessTest.value(reader, sql); }

    private static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains(marker)) {
            assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < deadline, marker); Thread.sleep(20);
        }
    }
}
