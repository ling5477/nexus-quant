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
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract B 的真实进程证明；控制器只安排暂停/断连，不授予或修复业务事实。 */
@EnabledIfSystemProperty(named="nq.b5", matches="true")
class B5AuthorityProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Test void authorityOrderingAndCommitOutcomes() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-authority/" + UUID.randomUUID());
        System.out.println("B5_AUTHORITY_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            int run = 0;
            for (String row : new String[]{"SENDER_WINS", "MALFORMED_ACK", "DEATH", "KILL_BEFORE_ARM", "RACE1", "RACE2", "RACE3",
                    "ROLLBACK", "REJECT", "BEFORE_DROP", "AFTER_DROP"}) {
                if (!System.getProperty("nq.b5.row", "ALL").equals("ALL")
                        && !row.equals(System.getProperty("nq.b5.row"))) continue;
                execute(pg, root.resolve(row), row, ++run);
            }
            assertTrue(run > 0);
        }
    }

    private void execute(B0Processes.Pg pg, Path dir, String row, int run) throws Exception {
        Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", row).put("controllerPid", ProcessHandle.current().pid());
        boolean fault = Set.of("ROLLBACK", "REJECT", "BEFORE_DROP", "AFTER_DROP").contains(row);
        boolean wire = row.endsWith("DROP");
        String database;
        try {
            try (var fixture = B0Fixture.create(pg)) {
                database = fixture.name();
                B4TransactionFaults.installDeferredRejection(fixture);
                var proxyEnv = B0Processes.cleanEnvironment(); proxyEnv.put("NQ_B4_DB", fixture.url());
                try (var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment());
                     var proxy = new B0Processes.Child(B4PgWireProxyMain.class, dir, "pg-wire", proxyEnv)) {
                    String endpoint = "http://127.0.0.1:" + venue.ready();
                    String proxied = fixture.url().replaceFirst("127\\.0\\.0\\.1:[0-9]+", "127.0.0.1:" + proxy.ready());
                    var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", fixture.url());
                    env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                    fixture.initialize(true, endpoint, env, true);
                    var aEnv = new LinkedHashMap<>(env); if (wire) aEnv.put("NQ_B0_DB", proxied);
                    proof.put("database", database).put("venuePid", venue.process.pid()).put("proxyPid", proxy.process.pid());
                    try (var reader = fixture.checker();
                         var a = new B0Processes.Child(B0NqProcessMain.class, dir, "nq-a", aEnv).awaitReady();
                         var b = new B0Processes.Child(B0NqProcessMain.class, dir, "nq-b", env)) {
                        a.ready(); b.ready();
                        a.send("SET_B5_VENUE b2t4");
                        proof.put("externalVenueInput", "okx");
                        proof.put("nqPid", a.process.pid()).put("restartPid", b.process.pid());
                        assertNotEquals(a.process.pid(), b.process.pid());
                        assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                        if (fault) {
                            a.send("ARM_B4_TX AUTHORITY " + (wire ? "WIRE" : row));
                            if (wire) proxy.send("ARM " + row);
                            a.startCommand("PLACE_B2");
                            if (wire) awaitLog(proxy, "B4_WIRE_CUT " + row);
                            assertTrue(a.process.waitFor(30, TimeUnit.SECONDS), "uncertain caller must fail without PLACE");
                            if (!wire) assertTrue(Files.readString(a.log).contains(row.equals("REJECT")
                                    ? "B4_DEFERRED_COMMIT_REJECTION" : "B4_EXPLICIT_ROLLBACK_BEFORE_COMMIT"));
                            awaitNoTransaction(reader);
                            assertEquals(row.equals("AFTER_DROP") ? "MAY_HAVE_ESCAPED" : "NOT_ARMED", state(reader));
                            assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                            proof.set("atCut", snapshot(reader));
                            assertTrue(b.send("PLACE_B2").endsWith("SENT"));
                            b.send("B5_RECOVERY");
                            assertEquals(row.equals("AFTER_DROP") ? "SENT" : "CANCELLED", status(reader));
                        } else {
                            boolean race = row.startsWith("RACE");
                            if (row.equals("MALFORMED_ACK")) control(endpoint, "MALFORMED_PLACE_ACK");
                            boolean preArm = race || row.equals("KILL_BEFORE_ARM");
                            a.send(preArm ? "ARM_B5_PRE_SEND" : "ARM_B5_POST_ARM");
                            a.send("BEGIN_PLACE_B2");
                            awaitLog(a, "B5_CUT " + (preArm ? "PRE_SEND" : "POST_ARM") + " transactionActive=false");
                            assertEquals(preArm ? "NOT_ARMED" : "MAY_HAVE_ESCAPED", state(reader));
                            assertEquals("SENT", status(reader));
                            proof.set("atCut", snapshot(reader));
                            assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                            if (row.equals("KILL_BEFORE_ARM")) {
                                b.send("ENGAGE"); a.send("RELEASE_B5_PRE_SEND"); a.send("AWAIT");
                                assertEquals("NOT_ARMED", state(reader)); assertEquals("SENT", status(reader));
                                assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                                b.send("B5_RECOVERY");
                            } else if (race) {
                                try (var workers = Executors.newFixedThreadPool(2)) {
                                    var start = new CyclicBarrier(2);
                                    var sender = workers.submit(() -> { start.await(5, TimeUnit.SECONDS); return a.send("RELEASE_B5_PRE_SEND"); });
                                    var recovery = workers.submit(() -> { start.await(5, TimeUnit.SECONDS); return b.send("B5_RECOVERY"); });
                                    sender.get(30, TimeUnit.SECONDS); recovery.get(30, TimeUnit.SECONDS);
                                }
                                a.send("AWAIT");
                            } else {
                                b.send("B5_RECOVERY");
                                assertEquals("SENT", status(reader));
                                assertEquals("MAY_HAVE_ESCAPED", state(reader));
                                assertTrue(b.send("PLACE_B2").endsWith("SENT"));
                                if (row.equals("DEATH")) {
                                    a.kill(); b.send("B5_RECOVERY");
                                    assertEquals("SENT", status(reader));
                                    b.close();
                                    try (var restarted = new B0Processes.Child(B0NqProcessMain.class, dir, "nq-restart", env)) {
                                        restarted.ready(); proof.put("newRestartPid", restarted.process.pid());
                                        assertNotEquals(a.process.pid(), restarted.process.pid());
                                        restarted.send("B5_RECOVERY");
                                        assertTrue(restarted.send("PLACE_B2").endsWith("SENT"));
                                        assertEquals("MAY_HAVE_ESCAPED", state(reader));
                                        assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                                    }
                                } else {
                                    a.send("RELEASE_B5_PRE_SEND"); a.send("AWAIT");
                                    if (row.equals("MALFORMED_ACK")) assertEquals("SENT", status(reader));
                                }
                            }
                            if (!row.equals("DEATH") && state(reader).equals("MAY_HAVE_ESCAPED")) {
                                assertNotEquals("CANCELLED", status(reader));
                                assertEquals(1, facts(endpoint).path("placeRequests").asInt());
                                b.send("ENGAGE");
                                control(endpoint, "FILL 10 0.01"); b.send("RECOVER");
                                new B2RealProcessProofTest().assertAccounting(reader, facts(endpoint));
                                assertEquals("FILLED", status(reader));
                                assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                                assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                                ObjectNode stable = snapshot(reader);
                                b.send("RECOVER"); b.send("PLACE_B2");
                                assertEquals(stable.path("orders"), snapshot(reader).path("orders"));
                            } else if (!row.equals("DEATH")) {
                                assertEquals("REVOKED_BEFORE_SEND", state(reader)); assertEquals("CANCELLED", status(reader));
                            }
                        }
                        proof.set("final", snapshot(reader)); proof.set("venue", facts(endpoint));
                        assertEquals("OKX", value(reader, "SELECT venue FROM orders"));
                        int expected = !fault && !row.equals("DEATH") && state(reader).equals("MAY_HAVE_ESCAPED") ? 1 : 0;
                        assertEquals(expected, facts(endpoint).path("placeRequests").asInt());
                        assertEquals(expected, facts(endpoint).path("places").asInt());
                        assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                    }
                }
            }
            assertTrue(pg.databaseAbsent(database)); proof.put("cleanup", true).put("result", "PASS");
            System.out.println("B5_AUTHORITY_PASS " + row);
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Path raw = dir.resolve("raw-proof.json"); Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-IMPL", run);
        }
    }

    private static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains(marker)) {
            assertTrue(child.process.isAlive(), "child exited before " + marker);
            assertTrue(System.nanoTime() < until, "cut timeout " + marker); Thread.sleep(20);
        }
    }
    private void awaitNoTransaction(Connection c) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!value(c, "SELECT count(*) FROM pg_stat_activity WHERE usename='nq_b0_app' AND xact_start IS NOT NULL").equals("0")) {
            assertTrue(System.nanoTime() < until); Thread.sleep(20);
        }
    }
    private String state(Connection c) throws Exception { return value(c, "SELECT state FROM ordinary_place_authorities"); }
    private String status(Connection c) throws Exception { return value(c, "SELECT status FROM orders"); }
    private String value(Connection c, String sql) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
    private ObjectNode snapshot(Connection c) throws Exception {
        ObjectNode n = mapper.createObjectNode();
        for (String t : new String[]{"orders", "ordinary_place_authorities", "trades", "ledger_entries"})
            n.set(t, mapper.readTree(value(c, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + t + " t")));
        return n;
    }
    private JsonNode facts(String endpoint) throws Exception {
        return mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    private void control(String endpoint, String command) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
