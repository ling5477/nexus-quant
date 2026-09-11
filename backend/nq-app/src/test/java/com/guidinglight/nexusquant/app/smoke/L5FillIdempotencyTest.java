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
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 从独立 Venue 经真实 Spring/reconciliation 到全部账务投影的最小证明，不执行规模 qualification。 */
@EnabledIfSystemProperty(named = "nq.l5.fill", matches = "true")
class L5FillIdempotencyTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void twoJvmSameFillHasOneDurableAccountingChain() throws Exception { contention(2); }
    @Test void fourJvmRepeatedDuplicateHasOneDurableAccountingChain() throws Exception { contention(4); }

    private void contention(int width) throws Exception {
        Path dir = directory("race-" + width + "-");
        ObjectNode proof = JSON.createObjectNode().put("actors", width);
        List<B0Processes.Child> children = new ArrayList<>();
        try {
            try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = environment(fixture, endpoint);
                fixture.initialize(true, endpoint, env);
                try (var bootstrap = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = bootstrap.createStatement()) {
                    s.execute("GRANT pg_read_all_stats TO nq_b0_reader");
                }
                try {
                    for (int i = 0; i < width; i++) children.add(new B0Processes.Child(B0NqProcessMain.class,
                            dir.resolve("actor-" + i), "nq", env).awaitReady());
                    assertEquals(width, children.stream().map(c -> c.process.pid()).distinct().count());
                    assertTrue(children.getFirst().send("PLACE").endsWith("ACCEPTED"));
                    control(endpoint, "FILL 0.1 0.01");
                    for (var child : children) assertEquals("ARMED L5_FILL", child.send("ARM_L5_FILL"));
                    for (var child : children) child.startCommand("RECOVER");
                    for (var child : children) awaitFile(child, "fill-cut");
                    try (var reader = fixture.checker();
                         var gate = DriverManager.getConnection(fixture.url(), "postgres", "")) {
                        assertEquals("0", value(reader, "SELECT count(*) FROM trades"));
                        gate.setAutoCommit(false);
                        try (var s = gate.createStatement()) { s.executeQuery("SELECT order_id FROM orders FOR UPDATE").close(); }
                        for (var child : children) Files.writeString(child.log.getParent().resolve("fill-release"), "release");
                        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                        while (Integer.parseInt(value(reader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND usename='nq_b0_app' AND wait_event_type='Lock'")) < width) {
                            assertTrue(System.nanoTime() < deadline, "all actors must actually wait on PostgreSQL locks");
                            Thread.sleep(20);
                        }
                        proof.put("observedPgWaiters", width);
                        gate.rollback();
                        int created = 0;
                        for (var child : children) {
                            String result = child.result();
                            assertTrue(List.of("RECOVER 0", "RECOVER 1").contains(result), result);
                            if (result.equals("RECOVER 1")) created++;
                        }
                        assertEquals(1, created);
                        exact(reader); ObjectNode stable = snapshot(reader);
                        for (int wave = 0; wave < 5; wave++) {
                            for (var child : children) child.startCommand("RECOVER");
                            for (var child : children) assertEquals("RECOVER 0", child.result());
                            assertEquals(stable, snapshot(reader));
                        }
                        for (int replay = 0; replay < 2; replay++) {
                            assertEquals("RECOVER 0", children.getFirst().send("RECOVER"));
                            assertEquals(stable, snapshot(reader));
                        }
                        proof.set("facts", stable); proof.set("venue", JSON.readTree(control(endpoint, null)));
                        assertEquals("0.1", proof.path("venue").path("order").path("accFillSz").asText());
                        assertEquals(1, proof.path("venue").path("fills").size());
                        proof.put("falseOverfill", 0).put("replayUnchanged", true).put("result", "PASS");
                    }
                } finally {
                    // 复现失败也逐个关闭；正式 qualification 使用相同的清理 owner。
                    B0Processes.closeChildren(children);
                }
            }
            assertTrue(children.stream().noneMatch(c -> c.process.isAlive()));
            System.out.println("L5_FILL_PASS contention=" + width + " pg_waiters=" + width + " trade=1 event=1 ledger=4 projection=0.1 replay_unchanged");
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally { Files.writeString(dir.resolve("raw-proof.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof)); }
    }

    @Test void committedTradeWithLostResponseIsInspectedAndReplayedBySuccessor() throws Exception {
        Path dir = directory("commit-unknown-");
        ObjectNode proof = JSON.createObjectNode();
        try {
            try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = environment(fixture, endpoint); fixture.initialize(true, endpoint, env);
                var proxyEnv = B0Processes.cleanEnvironment(); proxyEnv.put("NQ_B4_DB", fixture.url());
                try (var wire = new B0Processes.Child(B4PgWireProxyMain.class, dir, "wire", proxyEnv)) {
                    var first = new LinkedHashMap<>(env);
                    first.put("NQ_B0_DB", fixture.url().replaceFirst("127\\.0\\.0\\.1:[0-9]+", "127.0.0.1:" + wire.ready()));
                    try (var reader = fixture.checker();
                         var a = new B0Processes.Child(B0NqProcessMain.class, dir.resolve("owner"), "nq", first).awaitReady();
                         var b = new B0Processes.Child(B0NqProcessMain.class, dir.resolve("observer"), "nq", env).awaitReady()) {
                        assertTrue(a.send("PLACE").endsWith("ACCEPTED")); control(endpoint, "FILL 0.1 0.01");
                        assertEquals("ARMED TRADE WIRE", a.send("ARM_B4_TX TRADE WIRE"));
                        assertEquals("ARMED AFTER_PAUSE", wire.send("ARM AFTER_PAUSE"));
                        a.startCommand("RECOVER");
                        awaitLog(wire, "B4_WIRE_CUT AFTER_PAUSE SERVER_COMMIT_CONFIRMED_RESPONSE_WITHHELD");
                        assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries"));
                        proof.set("commitUnknown", snapshot(reader));
                        assertEquals("RECOVER 0", b.send("RECOVER")); exact(reader);
                        ObjectNode stable = snapshot(reader);
                        a.kill(); assertEquals("DROPPED", wire.send("DROP_PENDING"));
                        try (var successor = new B0Processes.Child(B0NqProcessMain.class, dir.resolve("successor"), "nq", env).awaitReady()) {
                            assertNotEquals(a.process.pid(), successor.process.pid());
                            for (int i = 0; i < 3; i++) assertEquals("RECOVER 0", successor.send("RECOVER"));
                            assertEquals(stable, snapshot(reader)); exact(reader);
                            proof.put("ownerPid", a.process.pid()).put("successorPid", successor.process.pid());
                        }
                        proof.set("recovered", stable); proof.put("result", "PASS");
                    }
                }
            }
            System.out.println("L5_FILL_PASS real_commit_response_withheld durable_truth_first restart_replay_unchanged");
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally { Files.writeString(dir.resolve("raw-proof.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof)); }
    }

    static Path directory(String label) throws Exception {
        Path root = Files.createDirectories(B0Processes.root().resolve("backend/nq-app/target/l5-fill-remediation"));
        Path dir = Files.createTempDirectory(root, label); System.out.println("L5_FILL_ROOT " + dir); return dir;
    }
    static Map<String, String> environment(B0Fixture f, String endpoint) {
        var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", f.url());
        env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test"); return env;
    }
    static String control(String endpoint, String command) throws Exception {
        B0Fixture.requireVenue(endpoint);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            var request = HttpRequest.newBuilder(URI.create(endpoint + (command == null ? "/facts" : "/control"))).timeout(Duration.ofSeconds(5));
            var response = client.send(command == null ? request.GET().build() : request.POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode()); return response.body();
        }
    }
    private static void exact(Connection reader) throws Exception {
        assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
        assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
        assertEquals("4", value(reader, "SELECT count(*) FROM ledger_entries"));
        assertEquals("4", value(reader, "SELECT count(DISTINCT idempotency_key) FROM ledger_entries"));
        assertEquals("0.10000000", value(reader, "SELECT sum(qty) FROM trades"));
        assertEquals("0.10000000", value(reader, "SELECT qty FROM positions"));
        assertEquals("0.10000000", value(reader, "SELECT balance FROM account_snapshots WHERE currency='BTC' ORDER BY snapshot_id DESC LIMIT 1"));
        assertEquals("0", value(reader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='idle in transaction (aborted)'"));
    }
    private static ObjectNode snapshot(Connection reader) throws Exception { return new B2RealProcessProofTest().snapshot(reader); }
    private static String value(Connection c, String sql) throws Exception {
        try (var s = c.createStatement()) { s.setQueryTimeout(5); try (var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); } }
    }
    private static void awaitFile(B0Processes.Child child, String name) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!Files.exists(child.log.getParent().resolve(name))) {
            assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < end, "missing fill cut"); Thread.sleep(20);
        }
    }
    private static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        while (!Files.readString(child.log).contains(marker)) {
            assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < end, "missing commit cut"); Thread.sleep(20);
        }
    }
}
