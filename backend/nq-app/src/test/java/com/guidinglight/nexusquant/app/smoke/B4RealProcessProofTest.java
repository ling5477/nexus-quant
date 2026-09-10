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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B4 原截点的当前候选回归；旧 producer 缺口由 B4TradeEventRemediationTest 独立重现。 */
@EnabledIfSystemProperty(named = "nq.b4", matches = "true")
class B4RealProcessProofTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final B2RealProcessProofTest accounting = new B2RealProcessProofTest();

    @Test void tradeCommitProcessDeathMustRecoverCanonicalTradeEvent() throws Exception {
        run(false);
        run(true);
    }

    private void run(boolean injectDeath) throws Exception {
        Path directory = B0Processes.root().resolve("backend/nq-app/target/b4-proof/" + UUID.randomUUID());
        Files.createDirectories(directory);
        System.out.println("B4_EVIDENCE " + directory);
        ObjectNode proof = mapper.createObjectNode().put("scenario", injectDeath
                        ? "AFTER_TRADE_COMMIT_BEFORE_TRADE_EVENT" : "HEALTHY_CONTROL")
                .put("repeat", 1).put("controllerPid", ProcessHandle.current().pid());
        try (var pg = B0Processes.Pg.start()) {
            String database;
            try (var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, directory, "venue", B0Processes.cleanEnvironment())) {
                database = fixture.name();
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env);
                proof.put("database", database).put("venuePid", venue.process.pid()).put("venueEndpoint", endpoint);
                try (var reader = fixture.checker();
                     var nq = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-a", env)) {
                    nq.ready(); proof.put("nqPid", nq.process.pid());
                    assertNotEquals(venue.process.pid(), nq.process.pid());
                    assertNotEquals(ProcessHandle.current().pid(), nq.process.pid());
                    assertEquals(B0Fixture.READER, value(reader, "SELECT current_user"));
                    proof.put("postgres", value(reader, "SHOW server_version"));
                    assertTrue(proof.path("postgres").asText().startsWith("16."));
                    assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                    proof.put("schema", "V50");
                    assertTrue(nq.send("PLACE_B2").endsWith("ACCEPTED"));
                    control(endpoint, "FILL 10 0.01");
                    if (injectDeath) {
                        assertEquals("ARMED AFTER_TRADE_COMMIT", nq.send("ARM_B4_TRADE_COMMIT"));
                        nq.startCommand("RECOVER");
                        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                        while (!Files.readString(nq.log).contains("B4_CUT AFTER_TRADE_COMMIT transactionActive=false")) {
                            assertTrue(nq.process.isAlive(), "NQ must reach real transaction boundary");
                            assertTrue(System.nanoTime() < deadline, "BLOCKED / B4_HARNESS_CONTROL_GAP");
                            Thread.sleep(20);
                        }
                        // 独立只读连接确认事务真正提交，不根据 Java 返回值猜测提交结果。
                        assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                        assertEquals("1", tradeEvents(reader), "new Trade and required event must commit atomically");
                        assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries"));
                        proof.set("atCut", snapshot(reader));
                        proof.set("venueAtCut", facts(endpoint));
                        proof.put("transactionOutcome", "COMMITTED_CONFIRMED_BY_INDEPENDENT_READER")
                                .put("cutMarker", "AFTER_TRADE_COMMIT transactionActive=false");
                    } else {
                        assertEquals("RECOVER 1", nq.send("RECOVER"));
                        assertEquals("1", tradeEvents(reader), "healthy control must exercise the real event oracle");
                        accounting.assertAccounting(reader, facts(endpoint));
                        proof.set("beforeRestart", snapshot(reader));
                    }
                    nq.kill(); assertFalse(nq.process.isAlive());
                    proof.put("oldProcessDeadBeforeRestart", true).put("deathExit", nq.process.exitValue());
                    try (var restarted = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-b", env)) {
                        restarted.ready(); proof.put("restartPid", restarted.process.pid());
                        assertNotEquals(nq.process.pid(), restarted.process.pid());
                        assertNotEquals(venue.process.pid(), restarted.process.pid());
                        assertEquals("RECOVER 0", restarted.send("RECOVER"));
                        assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                        accounting.assertAccounting(reader, facts(endpoint));
                        proof.set("afterRecovery", snapshot(reader));
                        JsonNode stable = accounting.snapshot(reader);
                        assertEquals("RECOVER 0", restarted.send("RECOVER"));
                        assertEquals(stable, accounting.snapshot(reader));
                        proof.set("afterReplay", snapshot(reader));
                        proof.set("finalVenue", facts(endpoint));
                        assertEquals(1, facts(endpoint).path("places").asInt());
                        assertEquals(1, facts(endpoint).path("placeRequests").asInt());
                        proof.put("blindRetries", 0).put("stableBusinessReplay", true)
                                .put("tradeEventCount", Integer.parseInt(tradeEvents(reader)));
                    }
                }
            }
            assertTrue(pg.databaseAbsent(database));
            proof.put("databaseRemoved", true).put("allChildProcessesExited", true);
        } finally {
            Path raw = directory.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, injectDeath ? "B4" : "B4-CTRL", 1);
        }
        assertEquals(1, proof.path("tradeEventCount").asInt(-1),
                "FAIL / L4_B4_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED: durable Trade has no canonical TradeExecuted after restart/replay");
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = accounting.snapshot(reader);
        for (String table : new String[]{"event_store", "audit_logs"}) {
            result.set(table, mapper.readTree(value(reader,
                    "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        }
        return result;
    }

    private String tradeEvents(Connection reader) throws Exception {
        return value(reader, "SELECT count(*) FROM event_store WHERE topic='trade.event.v1' AND event_type='TradeExecuted'");
    }

    private String value(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); return rows.getString(1);
        }
    }

    private JsonNode facts(String endpoint) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return mapper.readTree(response.body());
    }

    private void control(String endpoint, String command) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control"))
                .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(command)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }
}
