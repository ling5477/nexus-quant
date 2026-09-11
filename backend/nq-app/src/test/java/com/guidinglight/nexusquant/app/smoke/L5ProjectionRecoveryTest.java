package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 原 NQ Spring/RiskGate/adapter/独立 Venue 路径；只在 Ledger 提交边界注入真实 PG 网络中断。 */
@EnabledIfSystemProperty(named = "nq.l5.projection", matches = "true")
class L5ProjectionRecoveryTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private static Path directory(String prefix) throws Exception {
        Path parent = Files.createDirectories(B0Processes.root().resolve("backend/nq-app/target/l5-projection-remediation"));
        return Files.createTempDirectory(parent, prefix);
    }

    @Test void effectiveQuantityRatherThanRequestedIntentFeedsProjection() throws Exception {
        Path dir = directory("effective-");
        ObjectNode proof = json.createObjectNode().put("scenario", "effective_quantity_projection");
        System.out.println("L5_PROJECTION_EFFECTIVE_ROOT " + dir);
        try {
            try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", fixture.url());
                env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env); B5StrategyRunRecoveryProcessTest.seed(fixture);
                // 仅设置运行前的策略输入；effective decision、Order、Trade 与所有投影均由真实服务产生。
                try (var bootstrap = DriverManager.getConnection(fixture.url(), "postgres", "");
                     var statement = bootstrap.createStatement()) {
                    statement.execute("UPDATE strategy_definitions SET config_snapshot=jsonb_set(config_snapshot,'{quantity}','\"10.0005\"')");
                }
                try (var reader = fixture.checker();
                     var nq = new B0Processes.Child(B0NqProcessMain.class, dir, "nq", env).awaitReady()) {
                    assertTrue(nq.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                    control(endpoint, "FILL 10 0.01");
                    for (int i = 0; i < 3; i++) nq.send("RECOVER");
                    assertEquals("10.00050000", value(reader, "SELECT quantity FROM strategy_run_dispatch_work"));
                    assertEquals("10.00000000", value(reader, "SELECT effective_quantity FROM strategy_run_dispatch_work"));
                    assertEquals("10.00000000", value(reader, "SELECT qty FROM orders"));
                    assertEquals("10.00000000", value(reader, "SELECT sum(qty) FROM trades"));
                    assertEquals("10.00000000", value(reader, "SELECT qty FROM positions"));
                    assertEquals("10.00000000", value(reader, "SELECT balance FROM account_snapshots WHERE currency='BTC' ORDER BY snapshot_id DESC LIMIT 1"));
                    assertEquals("4", value(reader, "SELECT count(*) FROM ledger_entries"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                    ObjectNode stable = snapshot(reader); nq.send("RECOVER"); assertEquals(stable, snapshot(reader));
                    proof.set("facts", stable); proof.set("venue", facts(endpoint));
                    L5ProjectionConcurrencyTest.decimal("10", new BigDecimal(proof.path("venue").path("order").path("sz").asText()));
                    assertEquals(1, proof.path("venue").path("placeRequests").asInt());
                    proof.put("requestedQuantity", "10.0005").put("effectiveQuantity", "10.0")
                            .put("position", "10.0").put("snapshot", "10.0").put("replayUnchanged", true);
                }
            }
            proof.put("result", "PASS");
            System.out.println("L5_PROJECTION_PASS effective requested=10.0005 actual=10.0 position=10.0 snapshot=10.0");
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Files.writeString(dir.resolve("raw-proof.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
    }

    @Test void concurrentReconciliationSurvivesOwnerDeathBeforeAndAfterCommit() throws Exception {
        Path root = directory("recovery-");
        System.out.println("L5_PROJECTION_RECOVERY_ROOT " + root);
        try (var pg = B0Processes.Pg.startBounded()) {
            for (String mode : List.of("BEFORE_PAUSE", "AFTER_PAUSE")) execute(pg, root.resolve(mode), mode);
        }
    }

    private void execute(B0Processes.Pg pg, Path dir, String mode) throws Exception {
        Files.createDirectories(dir);
        ObjectNode proof = json.createObjectNode().put("mode", mode).put("controllerPid", ProcessHandle.current().pid());
        try {
            try (var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(L5VenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", fixture.url());
                env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env);
                // 仅向新建隔离库的只读观察者开放锁等待元数据，不增加任何业务表写权限。
                try (var bootstrap = DriverManager.getConnection(fixture.url(), "postgres", "");
                     var statement = bootstrap.createStatement()) {
                    statement.execute("GRANT pg_read_all_stats TO nq_b0_reader");
                }
                control(endpoint, "L5_OPEN");
                var proxyEnv = B0Processes.cleanEnvironment(); proxyEnv.put("NQ_B4_DB", fixture.url());
                try (var wire = new B0Processes.Child(B4PgWireProxyMain.class, dir, "wire", proxyEnv)) {
                    var firstEnv = new LinkedHashMap<>(env);
                    firstEnv.put("NQ_B0_DB", fixture.url().replaceFirst("127\\.0\\.0\\.1:[0-9]+", "127.0.0.1:" + wire.ready()));
                    try (var reader = fixture.checker();
                         var a = new B0Processes.Child(B0NqProcessMain.class, dir, "owner", firstEnv).awaitReady();
                         var b = new B0Processes.Child(B0NqProcessMain.class, dir, "competitor", env).awaitReady()) {
                        proof.put("ownerPid", a.process.pid()).put("competitorPid", b.process.pid()).put("venuePid", venue.process.pid());
                        assertTrue(a.send("PLACE_B2").endsWith("ACCEPTED"));
                        assertTrue(b.send("PLACE_B3_NEW").endsWith("ACCEPTED"));
                        control(endpoint, "FILL");
                        assertEquals("ARMED LEDGER WIRE", a.send("ARM_B4_TX LEDGER WIRE"));
                        assertEquals("ARMED " + mode, wire.send("ARM " + mode));
                        a.startCommand("RECOVER");
                        String marker = "B4_WIRE_CUT " + mode + " " + (mode.startsWith("BEFORE")
                                ? "COMMIT_NOT_FORWARDED" : "SERVER_COMMIT_CONFIRMED_RESPONSE_WITHHELD");
                        awaitLog(wire, marker); proof.put("wireBoundary", marker);
                        ObjectNode cut = snapshot(reader); proof.set("atCut", cut);
                        assertEquals(mode.startsWith("BEFORE") ? 0 : 4, cut.path("ledger_entries").size());
                        assertEquals(mode.startsWith("BEFORE") ? 0 : 1, cut.path("positions").size());
                        assertEquals(mode.startsWith("BEFORE") ? 0 : 2, cut.path("account_snapshots").size());
                        b.startCommand("RECOVER");
                        if (mode.startsWith("BEFORE")) {
                            long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                            while (Integer.parseInt(value(reader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND usename='nq_b0_app' AND wait_event_type='Lock'")) < 1) {
                                assertTrue(System.nanoTime() < end, "competitor must actually wait in PG"); Thread.sleep(20);
                            }
                            proof.put("competitorBlockedBeforeCommit", true);
                        } else {
                            assertTrue(b.result().startsWith("RECOVER "));
                            assertExact(reader, endpoint);
                            proof.put("competitorCompletedWhileOwnerAwaitedCommit", true);
                        }
                        assertTrue(a.process.isAlive()); a.kill();
                        assertEquals("DROPPED", wire.send("DROP_PENDING"));
                        if (mode.startsWith("BEFORE")) assertTrue(b.result().startsWith("RECOVER "));
                        try (var successor = new B0Processes.Child(B0NqProcessMain.class, dir, "successor", env).awaitReady()) {
                            assertNotEquals(a.process.pid(), successor.process.pid()); proof.put("successorPid", successor.process.pid());
                            for (int attempt = 0; attempt < 3; attempt++) assertTrue(successor.send("RECOVER").startsWith("RECOVER "));
                            assertExact(reader, endpoint);
                            ObjectNode stable = snapshot(reader); proof.set("afterRecovery", stable);
                            b.startCommand("RECOVER"); successor.startCommand("RECOVER");
                            assertTrue(b.result().startsWith("RECOVER ")); assertTrue(successor.result().startsWith("RECOVER "));
                            assertEquals(stable, snapshot(reader));
                            proof.put("replayUnchanged", true);
                            if (mode.startsWith("AFTER")) {
                                // 响应丢失后原事务每一个 durable identity 与原值都必须保留。
                                for (String table : List.of("trades", "ledger_entries", "ledger_events", "account_snapshots")) {
                                    for (JsonNode original : cut.path(table)) {
                                        boolean found = false;
                                        for (JsonNode current : stable.path(table)) if (original.equals(current)) found = true;
                                        assertTrue(found, "committed fact changed after ambiguous response: " + table);
                                    }
                                }
                            }
                        }
                        proof.set("venue", facts(endpoint));
                        proof.put("expectedPosition", "20.0").put("position", value(reader, "SELECT qty FROM positions"))
                                .put("snapshot", value(reader, "SELECT balance FROM account_snapshots WHERE currency='BTC' ORDER BY snapshot_id DESC LIMIT 1"));
                    }
                }
            }
            proof.put("result", "PASS").put("fixtureClosed", true);
            System.out.println("L5_PROJECTION_PASS " + mode + " two_reconciliation_jvms restarted_successor exact=20.0 replay_unchanged");
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Files.writeString(dir.resolve("raw-proof.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
    }

    private void assertExact(Connection reader, String endpoint) throws Exception {
        assertEquals("2", value(reader, "SELECT count(*) FROM orders WHERE status='FILLED'"));
        assertEquals("2", value(reader, "SELECT count(*) FROM trades"));
        assertEquals("2", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
        assertEquals("8", value(reader, "SELECT count(DISTINCT idempotency_key) FROM ledger_entries"));
        assertEquals("8", value(reader, "SELECT count(*) FROM ledger_entries"));
        assertEquals("0", value(reader, "SELECT count(*) FROM orders WHERE status NOT IN ('FILLED','CANCELLED','RISK_REJECTED')"));
        String expected = value(reader, "SELECT SUM(CASE WHEN o.side='BUY' THEN t.qty ELSE -t.qty END) FROM trades t JOIN orders o USING(order_id)");
        L5ProjectionConcurrencyTest.decimal("20", new BigDecimal(expected));
        L5ProjectionConcurrencyTest.decimal(expected, new BigDecimal(value(reader, "SELECT qty FROM positions")));
        L5ProjectionConcurrencyTest.decimal(expected, new BigDecimal(value(reader,
                "SELECT balance FROM account_snapshots WHERE currency='BTC' ORDER BY snapshot_id DESC LIMIT 1")));
        assertEquals(2, facts(endpoint).path("places").asInt());
    }

    private ObjectNode snapshot(Connection reader) throws Exception { return new B2RealProcessProofTest().snapshot(reader); }
    private static String value(Connection reader, String sql) throws Exception {
        try (var statement = reader.createStatement(); var row = statement.executeQuery(sql)) { assertTrue(row.next()); return row.getString(1); }
    }
    private static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        while (!Files.readString(child.log).contains(marker)) {
            assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < end, "missing wire cut: " + marker); Thread.sleep(20);
        }
    }
    private void control(String endpoint, String command) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
    private JsonNode facts(String endpoint) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return json.readTree(response.body());
    }
}
