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
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 复用 B0 真实 Spring/PG 与 B2 venue/账务 oracle，显式屏障证明 Kill 的 admission-time 合同。 */
@EnabledIfSystemProperty(named = "nq.b3", matches = "true")
class B3RealProcessProofTest {
    private enum Cut { PRE_ACCEPT, POST_ACCEPT_PRE_ACK, PENDING_RECONCILIATION, RESTART_PRE_ACK }
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final B2RealProcessProofTest b2 = new B2RealProcessProofTest();
    private final HashSet<Long> pids = new HashSet<>();
    private final HashSet<String> databases = new HashSet<>();

    @Test void v49AffectedKillAndRestartRegression() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b3-v49/" + UUID.randomUUID());
        Files.createDirectories(root);
        System.out.println("B3_V49_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            run(pg, root.resolve("PRE_ACCEPT"), Cut.PRE_ACCEPT, 1);
            run(pg, root.resolve("RESTART_PRE_ACK"), Cut.RESTART_PRE_ACK, 1);
        }
        assertEquals(2, databases.size()); assertEquals(5, pids.size());
    }

    @Test void qualifiesKillInFlightMatrix() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b3-proof/" + UUID.randomUUID());
        Files.createDirectories(root);
        System.out.println("B3_EVIDENCE " + root);
        try (var pg = B0Processes.Pg.start()) {
            for (int repeat = 1; repeat <= 3; repeat++) {
                for (Cut cut : Cut.values()) run(pg, root.resolve(cut + "-" + repeat), cut, repeat);
            }
        }
        assertEquals(12, databases.size());
        assertEquals(27, pids.size());
    }

    private void run(B0Processes.Pg pg, Path directory, Cut cut, int repeat) throws Exception {
        Files.createDirectories(directory);
        var proof = mapper.createObjectNode().put("scenario", cut.name()).put("repeat", repeat)
                .put("controllerPid", ProcessHandle.current().pid()).put("orderEnvironment", "SIM");
        int run = (repeat - 1) * Cut.values().length + cut.ordinal() + 1;
        String database;
        try (var venue = new B0Processes.Child(B2SyntheticVenueMain.class, directory, "venue", B0Processes.cleanEnvironment());
             var fixture = B0Fixture.create(pg)) {
            database = fixture.name();
            assertTrue(databases.add(database));
            String endpoint = "http://127.0.0.1:" + venue.ready();
            recordPid(proof, "venuePid", venue);
            proof.put("database", database).put("venueEndpoint", endpoint);
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env, true);
            try (var reader = fixture.checker(); var nq = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-a", env)) {
                nq.ready(); recordPid(proof, "nqPid", nq);
                assertEquals(B0Fixture.READER, value(reader, "SELECT current_user"));
                assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                proof.put("postgres", value(reader, "SHOW server_version")).put("schema", "V50");
                assertTrue(proof.path("postgres").asText().startsWith("16."));
                assertEquals("DISENGAGED", value(reader, "SELECT status FROM kill_switch_states"));
                assertEquals("false", value(reader, "SELECT has_table_privilege(current_user,'kill_switch_states','UPDATE')::text"));
                long initialVersion = Long.parseLong(value(reader, "SELECT version FROM kill_switch_states"));
                boolean pendingAck = cut != Cut.PENDING_RECONCILIATION;
                if (pendingAck) {
                    control(endpoint, cut == Cut.PRE_ACCEPT ? "HOLD_ACCEPTANCE" : "HOLD_PLACE");
                    assertEquals("BEGIN PLACE_B2", nq.send("BEGIN_PLACE_B2"));
                    awaitEvent(endpoint, cut == Cut.PRE_ACCEPT ? "PLACE_REQUEST_RECEIVED" : "PLACE_ACCEPTED");
                    assertEquals("SENT", value(reader, "SELECT status FROM orders"));
                    assertEquals(0, sequence(facts(endpoint), "PLACE_ACK_GENERATED"));
                    assertEquals(cut == Cut.PRE_ACCEPT ? 0 : 1, facts(endpoint).path("places").asInt());
                } else {
                    assertTrue(nq.send("PLACE_B2").endsWith("ACCEPTED"));
                    fills(endpoint);
                    assertEquals("0", value(reader, "SELECT count(*) FROM trades"));
                }
                proof.set("beforeKill", b2.snapshot(reader));
                proof.set("venueBeforeKill", facts(endpoint));
                proof.put("engageStartedEpochMillis", System.currentTimeMillis());
                assertEquals("ENGAGE ENGAGED " + (initialVersion + 1), nq.send("ENGAGE"));
                proof.put("engageCompletedEpochMillis", System.currentTimeMillis());
                assertKill(reader, initialVersion + 1);
                proof.set("killAfterEngage", killFacts(reader));
                // 此 marker 仅记录已读到 durable 状态，实际 transition 始终由 canonical service 完成。
                control(endpoint, "KILL_DURABLE_OBSERVED");
                JsonNode venueBeforeNew = facts(endpoint);
                assertTrue(nq.send("PLACE_B3_NEW").endsWith(" RISK_REJECTED"));
                assertEquals(venueBeforeNew, facts(endpoint), "new command must make zero venue calls");
                assertEquals("1", value(reader, "SELECT count(*) FROM orders WHERE status='RISK_REJECTED' AND reason='KILL_SWITCH_TRIGGERED'"));
                assertEquals("1", value(reader, "SELECT count(*) FROM risk_events WHERE decision='REJECT' AND reason='KILL_SWITCH_TRIGGERED'"));
                proof.set("afterNewCommand", b2.snapshot(reader));
                proof.put("postKillNewCommand", "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0");
                if (cut == Cut.PRE_ACCEPT) {
                    control(endpoint, "RELEASE_ACCEPTANCE");
                    assertTrue(nq.send("AWAIT").endsWith("ACCEPTED"));
                }
                if (cut == Cut.RESTART_PRE_ACK) {
                    nq.kill(); assertFalse(nq.process.isAlive());
                    proof.put("oldProcessDeadBeforeRestart", true);
                    fills(endpoint);
                    control(endpoint, "RELEASE_PLACE");
                    try (var restarted = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-b", env)) {
                        restarted.ready(); recordPid(proof, "restartPid", restarted);
                        assertKill(reader, initialVersion + 1);
                        proof.set("killAfterRestart", killFacts(reader));
                        recover(restarted, reader, proof, endpoint);
                        JsonNode before = facts(endpoint);
                        assertTrue(restarted.send("PLACE_B3_NEW").endsWith(" RISK_REJECTED"));
                        assertEquals(before, facts(endpoint));
                    }
                } else {
                    if (cut != Cut.PENDING_RECONCILIATION) fills(endpoint);
                    recover(nq, reader, proof, endpoint);
                    if (cut == Cut.POST_ACCEPT_PRE_ACK) {
                        assertEquals(0, sequence(facts(endpoint), "PLACE_ACK_GENERATED"));
                        JsonNode before = b2.snapshot(reader);
                        control(endpoint, "RELEASE_PLACE");
                        assertTrue(nq.send("AWAIT").endsWith("FILLED"));
                        assertEquals(before, b2.snapshot(reader), "late ACK must not overwrite reconciled facts/version");
                        proof.put("staleAckSnapshotUnchanged", true);
                    }
                }
                assertKill(reader, initialVersion + 1);
                proof.set("finalKill", killFacts(reader));
                proof.set("finalDb", b2.snapshot(reader));
                JsonNode finalVenue = facts(endpoint); proof.set("finalVenue", finalVenue);
                assertEquals(1, finalVenue.path("places").asInt());
                assertEquals(1, finalVenue.path("placeRequests").asInt(), "blind PLACE retries must be zero");
                assertEquals(0, finalVenue.path("cancels").asInt());
                assertEquals("filled", finalVenue.path("order").path("state").asText());
                long marker = sequence(finalVenue, "KILL_DURABLE_OBSERVED");
                long accepted = sequence(finalVenue, "PLACE_ACCEPTED");
                assertTrue(marker > 0 && accepted > 0);
                if (cut == Cut.PRE_ACCEPT) assertTrue(marker < accepted);
                else assertTrue(accepted < marker);
                if (pendingAck) assertTrue(marker < sequence(finalVenue, "PLACE_ACK_GENERATED"));
                else assertTrue(sequence(finalVenue, "FILL") < marker);
                assertTrue(marker < sequence(finalVenue, "QUERY_ORDER"));
                long beforeVersion = proof.path("beforeKill").path("orders").get(0).path("version").asLong();
                assertTrue(Long.parseLong(value(reader, "SELECT version FROM orders WHERE status='FILLED'")) >= beforeVersion);
                proof.put("blindRetries", 0).put("result", "PASS");
                write(directory, proof, run, "raw-proof.json");
                System.out.println("B3_SCENARIO_PASS " + cut + " repeat=" + repeat);
            }
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failureType", failure.getClass().getName())
                    .put("failure", String.valueOf(failure.getMessage()));
            write(directory, proof, run, "raw-failed-proof.json");
            throw failure;
        }
        assertTrue(pg.databaseAbsent(database));
    }

    private void recover(B0Processes.Child nq, Connection reader, ObjectNode proof, String endpoint) throws Exception {
        control(endpoint, "DUPLICATE_REPORTS");
        proof.put("recoveryStartedEpochMillis", System.currentTimeMillis());
        assertEquals("RECOVER 3", nq.send("RECOVER"));
        proof.put("recoveryCompletedEpochMillis", System.currentTimeMillis());
        assertEquals("1", value(reader, "SELECT count(*) FROM orders WHERE status='FILLED' AND trade_env='SIM'"));
        assertEquals("3", value(reader, "SELECT count(*) FROM trades"));
        b2.assertAccounting(reader, facts(endpoint));
        JsonNode before = b2.snapshot(reader);
        assertEquals("RECOVER 0", nq.send("RECOVER"));
        assertEquals(before, b2.snapshot(reader), "replay must preserve all business facts and versions");
        proof.set("afterRecoveryReplay", before);
    }

    private void assertKill(Connection reader, long version) throws Exception {
        assertEquals("ENGAGED", value(reader, "SELECT status FROM kill_switch_states"));
        assertEquals(Long.toString(version), value(reader, "SELECT version FROM kill_switch_states"));
        assertEquals("1", value(reader, "SELECT count(*) FROM kill_switch_events WHERE from_status='DISENGAGED' AND to_status='ENGAGED' AND source='OPERATOR_ENGAGE' AND reason_code='B3_IN_FLIGHT'"));
    }

    private JsonNode killFacts(Connection reader) throws Exception {
        // 不导出随机 event UUID；读取实际 transition、actor、trace、version 和时间。
        return mapper.readTree(value(reader, "SELECT jsonb_build_object('state',(SELECT to_jsonb(s) FROM kill_switch_states s),"
                + "'events',(SELECT jsonb_agg(to_jsonb(e)-'id' ORDER BY occurred_at) FROM kill_switch_events e))::text"));
    }

    private void fills(String endpoint) throws Exception {
        control(endpoint, "FILL 4 0"); control(endpoint, "FILL 3 0.01"); control(endpoint, "FILL 3 0.02");
    }

    private void recordPid(ObjectNode proof, String field, B0Processes.Child child) {
        assertNotEquals(ProcessHandle.current().pid(), child.process.pid());
        assertTrue(pids.add(child.process.pid()), "each run must use independent process identity");
        proof.put(field, child.process.pid());
    }

    private void write(Path directory, ObjectNode proof, int run, String filename) throws Exception {
        Path raw = directory.resolve(filename);
        Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        SyntheticEvidenceExport.write(raw, "B3", run);
    }

    private void awaitEvent(String endpoint, String type) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (sequence(facts(endpoint), type) == 0) {
            assertTrue(System.nanoTime() < deadline, "BLOCKED / B3_HARNESS_CONTROL_GAP: " + type);
            Thread.sleep(10);
        }
    }

    private long sequence(JsonNode venue, String type) {
        for (JsonNode event : venue.path("events")) if (type.equals(event.path("type").asText())) return event.path("sequence").asLong();
        return 0;
    }

    private JsonNode facts(String endpoint) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return mapper.readTree(response.body());
    }

    private void control(String endpoint, String command) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    private String value(Connection reader, String sql) throws Exception {
        try (var statement = reader.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); return rows.getString(1);
        }
    }
}
