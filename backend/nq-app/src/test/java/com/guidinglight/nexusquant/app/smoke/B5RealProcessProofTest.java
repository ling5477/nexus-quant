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
import static org.junit.jupiter.api.Assertions.*;

/** 两个真实 Spring JVM 竞争同一订单；红色断言要求恢复终态后原发送者失去外发能力。 */
@EnabledIfSystemProperty(named = "nq.b5", matches = "true")
class B5RealProcessProofTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Test void staleSenderMustNotPlaceAfterAnotherProcessFinalizesNotFound() throws Exception {
        Path directory = B0Processes.root().resolve("backend/nq-app/target/b5-proof/" + UUID.randomUUID());
        Files.createDirectories(directory);
        System.out.println("B5_EVIDENCE " + directory);
        ObjectNode proof = mapper.createObjectNode().put("scenario", "ORDINARY_PRE_SEND_RECOVERY_STALE_RESUME")
                .put("controllerPid", ProcessHandle.current().pid());
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
                     var a = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-a", env).awaitReady();
                     var b = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-b", env)) {
                    a.ready(); b.ready();
                    assertNotEquals(a.process.pid(), b.process.pid());
                    assertNotEquals(a.process.pid(), venue.process.pid());
                    assertNotEquals(b.process.pid(), venue.process.pid());
                    proof.put("nqPid", a.process.pid()).put("restartPid", b.process.pid());
                    proof.put("topology", "A_AND_B_CONCURRENT_SAME_PG_SAME_VENUE_NO_RESTART");
                    proof.put("postgres", value(reader, "SHOW server_version"));
                    assertTrue(proof.path("postgres").asText().startsWith("16."));
                    assertEquals(B0Fixture.READER, value(reader, "SELECT current_user"));
                    assertEquals("50", value(reader, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                    a.send("SET_B5_VENUE b2t4");
                    proof.put("externalVenueInput", "okx");
                    assertEquals("ARMED B5_PRE_SEND", a.send("ARM_B5_PRE_SEND"));
                    assertEquals("BEGIN PLACE_B2", a.send("BEGIN_PLACE_B2"));
                    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                    while (!Files.readString(a.log).contains("B5_CUT PRE_SEND transactionActive=false")) {
                        assertTrue(a.process.isAlive());
                        assertTrue(System.nanoTime() < deadline, "BLOCKED / B5_HARNESS_CONTROL_GAP");
                        Thread.sleep(20);
                    }
                    proof.put("cutObservedAt", System.currentTimeMillis());
                    assertEquals("SENT", value(reader, "SELECT status FROM orders"));
                    assertEquals("OKX", value(reader, "SELECT venue FROM orders"));
                    assertEquals("NOT_ARMED", value(reader, "SELECT state FROM ordinary_place_authorities"));
                    assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                    proof.set("atCut", snapshot(reader));
                    // 重复命令直接进入 ordinary service，控制器没有按数据库结果跳过调用。
                    assertTrue(b.send("PLACE_B2").endsWith("SENT"));
                    proof.set("afterDuplicate", snapshot(reader));
                    assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                    b.send("B5_RECOVERY");
                    assertEquals("CANCELLED", value(reader, "SELECT status FROM orders"));
                    assertEquals("REVOKED_BEFORE_SEND", value(reader, "SELECT state FROM ordinary_place_authorities"));
                    proof.put("recoveryCommittedAt", System.currentTimeMillis());
                    proof.set("afterRecovery", snapshot(reader));
                    proof.set("venueBeforeResume", facts(endpoint));
                    assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                    assertEquals("RELEASED B5_PRE_SEND", a.send("RELEASE_B5_PRE_SEND"));
                    proof.put("releasedAt", System.currentTimeMillis());
                    a.send("AWAIT");
                    proof.set("afterResume", snapshot(reader));
                    proof.set("finalVenue", facts(endpoint));
                    proof.put("completedAt", System.currentTimeMillis());
                    proof.put("latePlaceRequests", facts(endpoint).path("placeRequests").asInt());
                }
            }
            assertTrue(pg.databaseAbsent(database));
            proof.put("databaseRemoved", true).put("allChildProcessesExited", true);
        } finally {
            Path raw = directory.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5", 1);
        }
        assertEquals(0, proof.path("latePlaceRequests").asInt(-1),
                "FAIL / L4_B5_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED: stale ordinary sender dispatched after durable recovery finality");
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : new String[]{"orders", "ordinary_place_authorities", "trades", "event_store", "ledger_entries", "audit_logs"}) {
            result.set(table, mapper.readTree(value(reader,
                    "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        }
        result.put("tradeExecuted", value(reader,
                "SELECT count(*) FROM event_store WHERE topic='trade.event.v1' AND event_type='TradeExecuted'"));
        return result;
    }

    private String value(Connection reader, String sql) throws Exception {
        try (var statement = reader.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); return rows.getString(1);
        }
    }

    private JsonNode facts(String endpoint) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return mapper.readTree(response.body());
    }
}
