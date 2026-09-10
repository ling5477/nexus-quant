package com.guidinglight.nexusquant.app.smoke;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 PostgreSQL wire COMMIT 前/后断连；异常类型不用于推断事务是否提交。 */
@EnabledIfSystemProperty(named = "nq.b5.v51", matches = "true")
class B5V51CommitProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void admissionPreparationAndTerminalCommitUnknownReadDurableTruth() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-v51-commit/" + UUID.randomUUID());
        System.out.println("B5_V51_COMMIT_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            int run = 0;
            for (String boundary : List.of("A", "B", "C")) for (String mode : List.of("BEFORE_DROP", "AFTER_DROP")) {
                execute(pg, root.resolve(boundary + "_" + mode), boundary, mode, ++run);
            }
        }
    }

    private void execute(B0Processes.Pg pg, Path dir, String boundary, String mode, int run) throws Exception {
        Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("boundary", boundary).put("mode", mode)
                .put("controllerPid", ProcessHandle.current().pid());
        try (var fixture = B0Fixture.create(pg)) {
            var wireEnv = B0Processes.cleanEnvironment(); wireEnv.put("NQ_B4_DB", fixture.url());
            try (var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment());
                 var wire = new B0Processes.Child(B4PgWireProxyMain.class, dir, "wire", wireEnv)) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", fixture.url());
                env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env); B5StrategyRunRecoveryProcessTest.seed(fixture);
                var aEnv = new LinkedHashMap<>(env);
                aEnv.put("NQ_B0_DB", fixture.url().replaceFirst("127\\.0\\.0\\.1:[0-9]+", "127.0.0.1:" + wire.ready()));
                try (var reader = fixture.checker();
                     var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", aEnv).awaitReady();
                     var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady()) {
                    proof.put("database", fixture.name()).put("nqPid", a.process.pid()).put("restartPid", b.process.pid())
                            .put("venuePid", venue.process.pid()).put("proxyPid", wire.process.pid());
                    if (boundary.equals("C")) {
                        assertTrue(a.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                        fill(endpoint); b.send("RECOVER"); b.send("RECOVER");
                        assertEquals("RUNNING", value(reader, "SELECT status FROM strategy_runs"));
                        assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                    }
                    a.send("ARM_V51_TX " + boundary + " WIRE"); wire.send("ARM " + mode);
                    a.startCommand(boundary.equals("C") ? "RECOVER_V51_ALL" : "RUN_B5_STRATEGY");
                    awaitLog(wire, "B4_WIRE_CUT " + mode);
                    a.kill();
                    proof.set("independentTruth", snapshot(reader));
                    boolean committed = mode.equals("AFTER_DROP");
                    if (boundary.equals("A")) {
                        assertEquals(committed ? "1" : "0", value(reader, "SELECT count(*) FROM strategy_runs"));
                        assertEquals(committed ? "1" : "0", value(reader, "SELECT count(*) FROM strategy_run_dispatch_work"));
                        assertEquals(committed ? "1" : "0", value(reader, "SELECT count(*) FROM strategy_schedules WHERE last_triggered_at IS NOT NULL"));
                    } else if (boundary.equals("B")) {
                        assertEquals(committed ? "1" : "0", value(reader, "SELECT count(*) FROM orders"));
                        assertEquals(committed ? "1" : "0", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                    } else assertEquals(committed ? "SUCCEEDED" : "RUNNING", value(reader, "SELECT status FROM strategy_runs"));
                    if (boundary.equals("A") && !committed) assertTrue(b.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                    else b.send("RECOVER_V51_ALL");
                    if (!boundary.equals("C")) { fill(endpoint); b.send("RECOVER"); b.send("RECOVER"); }
                    b.send("RECOVER_V51_ALL");
                    assertEquals("SUCCEEDED", value(reader, "SELECT status FROM strategy_runs"));
                    for (String table : List.of("strategy_runs", "strategy_run_dispatch_work", "orders", "ordinary_place_authorities", "trades"))
                        assertEquals("1", value(reader, "SELECT count(*) FROM " + table));
                    assertEquals("1", value(reader, "SELECT count(*) FROM b5_run_transitions WHERE new_status='SUCCEEDED'"));
                    ObjectNode stable = snapshot(reader); b.send("RECOVER_V51_ALL"); assertEquals(stable, snapshot(reader));
                    try (var client = HttpClient.newHttpClient()) {
                        var venueFacts = mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
                        assertEquals(1, venueFacts.path("placeRequests").asInt()); proof.set("venue", venueFacts);
                    }
                    proof.set("final", snapshot(reader)); proof.put("result", "PASS");
                    System.out.println("B5_V51_COMMIT_PASS " + boundary + " " + mode);
                }
            }
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Path raw = dir.resolve("raw-proof.json"); Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-V51C", run);
        }
    }

    private ObjectNode snapshot(Connection c) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : List.of("strategy_runs", "strategy_run_dispatch_work", "strategy_schedules", "orders", "ordinary_place_authorities", "trades"))
            result.set(table, mapper.readTree(value(c, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        return result;
    }
    private void fill(String endpoint) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString("FILL 10 0.01")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
    private static String value(Connection c, String sql) throws Exception { return B5StrategyRunRecoveryProcessTest.value(c, sql); }
    private static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains(marker)) { assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < deadline, marker); Thread.sleep(20); }
    }
}
