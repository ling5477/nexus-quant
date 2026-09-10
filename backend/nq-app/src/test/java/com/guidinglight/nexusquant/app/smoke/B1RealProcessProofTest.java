package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B1 普通链路：独立 venue 制造网络不确定性，只读 checker 从真实进程和数据库判定收敛。 */
@EnabledIfSystemProperty(named = "nq.b1", matches = "true")
class B1RealProcessProofTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test void v49AffectedTimeoutAndLostAckRegression() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b1-v49/" + UUID.randomUUID());
        Files.createDirectories(root);
        System.out.println("B1_V49_ROOT " + root);
        run(root, "L4-AT-01", 1);
        run(root, "L4-LA-02", 1);
    }

    @Test void qualifiesCurrentOrdinaryRows() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b1-proof/" + UUID.randomUUID());
        Files.createDirectories(root);
        System.out.println("B1_EVIDENCE " + root);
        for (String scenario : new String[]{"L4-AT-01", "L4-LA-02"}) {
            for (int repeat = 1; repeat <= 3; repeat++) run(root, scenario, repeat);
        }
        var databases = new HashSet<String>();
        var venues = new HashSet<Long>();
        for (String scenario : new String[]{"L4-AT-01", "L4-LA-02"}) {
            for (int repeat = 1; repeat <= 3; repeat++) {
                JsonNode proof = mapper.readTree(root.resolve(scenario + "-" + repeat + "/proof.json").toFile());
                assertEquals("PASS", proof.path("verdict").asText());
                assertTrue(proof.path("resourcesClosed").asBoolean());
                assertTrue(databases.add(proof.path("db").asText()));
                assertTrue(venues.add(proof.path("venuePid").asLong()));
                check(proof);
            }
        }
        System.out.println("B1_ALL_ROWS PASS rows=2 independentRuns=6");
    }

    private void run(Path root, String scenario, int repeat) throws Exception {
        boolean lost = scenario.equals("L4-LA-02");
        Path directory = root.resolve(scenario + "-" + repeat);
        Files.createDirectories(directory);
        ObjectNode proof = mapper.createObjectNode().put("scenario", scenario).put("repeat", repeat)
                .put("controllerPid", ProcessHandle.current().pid()).put("verdict", "NOT_COMPLETED");
        try {
            try (var pg = B0Processes.Pg.start();
                 var venue = new B0Processes.Child(B0SyntheticVenueMain.class, directory, "venue", B0Processes.cleanEnvironment());
                 var fixture = B0Fixture.create(pg)) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                proof.put("venuePid", venue.process.pid()).put("db", fixture.name()).put("dbUrl", fixture.url());
                Map<String, String> env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env);
                control(endpoint, lost ? "B1_LOST_ACK" : "B1_ACCEPTED_TIMEOUT");
                try (var checker = fixture.checker();
                     var first = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-a", env)) {
                    first.ready();
                    proof.put("firstPid", first.process.pid());
                    assertNotEquals(venue.process.pid(), first.process.pid());
                    assertNotEquals(ProcessHandle.current().pid(), first.process.pid());
                    proof.put("pgVersion", value(checker, "SHOW server_version"));
                    assertTrue(proof.path("pgVersion").asText().startsWith("16."));
                    assertEquals("51", value(checker, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                    assertEquals(fixture.name(), value(checker, "SELECT current_database()"));
                    assertEquals("nq_b0_reader", value(checker, "SELECT current_user"));
                    proof.set("preFault", snapshot(checker));
                    first.startCommand("PLACE");
                    JsonNode accepted = awaitAccepted(endpoint, lost);
                    proof.set("venueAtFault", accepted);
                    proof.set("atFault", snapshot(checker));
                    assertEquals("SENT", proof.path("atFault").path("orders").get(0).path("status").asText());
                    assertEquals(1, accepted.path("places").asInt());
                    assertEquals(0, accepted.path("delivered").asInt());
                    if (lost) {
                        assertFalse(Files.readString(first.log).contains("B0_RESULT PLACE"));
                        proof.put("callerAckAbsentBeforeDeath", true);
                        assertEquals(0, count(accepted, "QUERY_ORDER"));
                        first.kill();
                        proof.put("oldProcessDeadBeforeRecovery", !first.process.isAlive());
                        control(endpoint, "RELEASE_LOST");
                        proof.set("afterDeath", snapshot(checker));
                        try (var second = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-b", env)) {
                            second.ready();
                            proof.put("recoveryPid", second.process.pid());
                            assertNotEquals(first.process.pid(), second.process.pid());
                            proveRecovery(second, checker, endpoint, proof);
                        }
                    } else {
                        proof.put("placeResult", first.result());
                        assertTrue(proof.path("placeResult").asText().endsWith("ACCEPTED"));
                        String log = Files.readString(first.log);
                        assertTrue(log.contains("okx_query_confirm_place_started"));
                        proof.put("callerTimeoutQueryConfirm", true);
                        JsonNode queried = facts(endpoint);
                        assertEquals(0, count(queried, "ACK_GENERATED"));
                        long elapsed = event(queried, "QUERY_ORDER").path("nanoTime").asLong()
                                - event(queried, "REQUEST_RECEIVED").path("nanoTime").asLong();
                        proof.put("timeoutToQueryNanos", elapsed);
                        assertTrue(elapsed >= Duration.ofMillis(1800).toNanos(), "real 2s HTTP timeout required");
                        proveRecovery(first, checker, endpoint, proof);
                    }
                    // 已经记账后再次更换 JVM；相同 DB、Order 和 venue identity 不产生新业务事实。
                    first.close();
                    try (var replay = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-replay", env)) {
                        replay.ready();
                        proof.put("replayPid", replay.process.pid());
                        proof.put("restartReplayResult", replay.send("RECOVER"));
                        proof.set("afterRestartReplay", snapshot(checker));
                        assertEquals(business(proof.path("finalDb")), business(proof.path("afterRestartReplay")));
                    }
                    proof.set("venueFinal", facts(endpoint));
                    check(proof);
                    negativeChecks(proof);
                    proof.put("negativeChecks", 8);
                    proof.put("verdict", "PASS");
                }
            }
            proof.put("resourcesClosed", true);
        } catch (Exception | AssertionError failure) {
            proof.put("verdict", "FAIL").put("failure", failure.toString());
            throw failure;
        } finally {
            mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("proof.json").toFile(), proof);
            System.out.println("B1_RUN " + scenario + " repeat=" + repeat + " verdict=" + proof.path("verdict").asText()
                    + " evidence=" + directory.resolve("proof.json"));
        }
    }

    private void proveRecovery(B0Processes.Child nq, Connection checker, String endpoint, ObjectNode proof) throws Exception {
        proof.put("initialRecoveryResult", nq.send("RECOVER"));
        proof.set("beforeFill", snapshot(checker));
        assertEquals("ACCEPTED", value(checker, "SELECT status FROM orders"));
        assertEquals("0", value(checker, "SELECT count(*) FROM trades"));
        assertEquals("0", value(checker, "SELECT count(*) FROM ledger_entries"));
        JsonNode queried = facts(endpoint);
        assertTrue(count(queried, "QUERY_ORDER") >= 1);
        String client = value(checker, "SELECT client_order_id FROM orders");
        assertEquals(client, event(queried, "QUERY_ORDER").path("client").asText());
        assertEquals(1, queried.path("places").asInt());
        control(endpoint, "FILL");
        proof.put("fillRecoveryResult", nq.send("RECOVER"));
        proof.set("finalDb", snapshot(checker));
        assertEquals("FILLED", value(checker, "SELECT status FROM orders"));
        assertEquals("1", value(checker, "SELECT count(*) FROM trades"));
        assertEquals("4", value(checker, "SELECT count(*) FROM ledger_entries"));
        proof.put("replayResult", nq.send("RECOVER"));
        proof.set("afterReplay", snapshot(checker));
        assertEquals(business(proof.path("finalDb")), business(proof.path("afterReplay")));
    }

    private void check(JsonNode proof) throws Exception {
        for (String phase : new String[]{"preFault", "atFault", "beforeFill", "finalDb", "afterReplay", "afterRestartReplay"}) {
            assertEquals(proof.path("db"), proof.path(phase).path("databaseIdentity"));
        }
        assertEquals(0, proof.path("beforeFill").path("trades").size());
        assertEquals(0, proof.path("beforeFill").path("ledger_entries").size());
        JsonNode venue = proof.path("venueFinal");
        assertEquals(1, venue.path("places").asInt());
        assertEquals(1, venue.path("orders").asInt());
        assertEquals(1, count(venue, "REQUEST_RECEIVED"));
        assertEquals(1, count(venue, "VENUE_ACCEPTED"));
        assertEquals(0, venue.path("delivered").asInt());
        assertTrue(count(venue, "QUERY_ORDER") > 0);
        assertEquals(1, count(venue, "FILL"));
        assertTrue(event(venue, "QUERY_ORDER").path("sequence").asInt() > event(venue, "VENUE_ACCEPTED").path("sequence").asInt());
        assertTrue(event(venue, "FILL").path("sequence").asInt() > event(venue, "QUERY_ORDER").path("sequence").asInt());
        boolean lost = proof.path("scenario").asText().equals("L4-LA-02");
        assertEquals(lost ? 1 : 0, count(venue, "ACK_GENERATED"));
        if (lost) {
            assertTrue(event(venue, "ACK_GENERATED").path("sha256").asText().matches("[a-f0-9]{64}"));
            byte[] bytes = event(venue, "ACK_GENERATED").path("body").asText().getBytes(StandardCharsets.UTF_8);
            assertEquals(bytes.length, event(venue, "ACK_GENERATED").path("bytes").asInt());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    event(venue, "ACK_GENERATED").path("sha256").asText());
            assertTrue(proof.path("callerAckAbsentBeforeDeath").asBoolean());
            assertTrue(proof.path("oldProcessDeadBeforeRecovery").asBoolean());
            assertNotEquals(proof.path("firstPid"), proof.path("recoveryPid"));
        } else assertTrue(proof.path("callerTimeoutQueryConfirm").asBoolean());
        JsonNode db = proof.path("finalDb");
        assertEquals(1, db.path("orders").size());
        JsonNode order = db.path("orders").get(0);
        assertEquals("FILLED", order.path("status").asText());
        assertEquals("SIM", order.path("trade_env").asText());
        assertEquals(venue.path("data").get(0).path("ordId"), order.path("external_order_id"));
        assertEquals(venue.path("data").get(0).path("clOrdId"), order.path("client_order_id"));
        assertTrue(order.path("version").asLong() >= proof.path("atFault").path("orders").get(0).path("version").asLong());
        assertEquals(1, db.path("trades").size());
        JsonNode trade = db.path("trades").get(0);
        assertEquals(order.path("order_id"), trade.path("order_id"));
        assertEquals("b0-fill-" + order.path("external_order_id").asText(), trade.path("exchange_trade_id").asText());
        assertEquals(0, trade.path("price").decimalValue().compareTo(new BigDecimal("100")));
        assertEquals(0, trade.path("qty").decimalValue().compareTo(new BigDecimal("0.1")));
        assertEquals(0, trade.path("fee").decimalValue().abs().compareTo(new BigDecimal("0.01")));
        assertEquals(4, db.path("ledger_entries").size());
        assertEquals(4, db.path("ledger_events").size());
        BigDecimal net = BigDecimal.ZERO;
        for (JsonNode entry : db.path("ledger_entries")) {
            assertEquals(trade.path("trade_id"), entry.path("ref_id"));
            net = net.add(entry.path("delta").decimalValue());
        }
        assertEquals(0, net.compareTo(BigDecimal.ZERO));
        assertTrue(db.path("audit_logs").size() > 0);
        assertTrue(db.path("event_store").size() > 0);
        assertEquals(0, db.path("execution_intents").size());
        assertEquals(0, db.path("execution_receipts").size());
        assertEquals(business(db), business(proof.path("afterReplay")));
        assertEquals(business(db), business(proof.path("afterRestartReplay")));
    }

    private ObjectNode snapshot(Connection connection) throws Exception {
        ObjectNode result = mapper.createObjectNode().put("databaseIdentity", value(connection, "SELECT current_database()"));
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        try {
            for (String table : new String[]{"orders", "trades", "ledger_entries", "ledger_events", "positions",
                    "account_snapshots", "audit_logs", "event_store", "execution_intents", "execution_receipts"}) {
                result.set(table, mapper.readTree(value(connection,
                        "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
            }
            connection.commit();
            return result;
        } finally { connection.setAutoCommit(true); }
    }

    private void negativeChecks(ObjectNode proof) {
        for (int mutation = 0; mutation < 8; mutation++) {
            ObjectNode bad = proof.deepCopy();
            switch (mutation) {
                case 0 -> ((ObjectNode) bad.path("venueFinal")).put("places", 2);
                case 1 -> bad.remove("atFault");
                case 2 -> ((ObjectNode) bad.path("finalDb")).put("databaseIdentity", "wrong_run");
                case 3 -> ((ArrayNode) bad.path("finalDb").path("ledger_entries")).remove(0);
                case 4 -> ((ObjectNode) bad.path("finalDb").path("trades").get(0)).put("exchange_trade_id", "wrong_fill");
                case 5 -> ((ObjectNode) bad.path("venueFinal")).put("delivered", 1);
                case 6 -> ((ObjectNode) bad.path("finalDb").path("orders").get(0)).put("version", -1);
                case 7 -> ((ObjectNode) bad.path("venueFinal")).putArray("events");
                default -> throw new AssertionError("unknown mutation");
            }
            assertThrows(AssertionError.class, () -> check(bad), "forged PASS must fail mutation=" + mutation);
        }
    }

    private ObjectNode business(JsonNode snapshot) {
        ObjectNode result = mapper.createObjectNode();
        for (String table : new String[]{"orders", "trades", "ledger_entries", "ledger_events", "positions", "account_snapshots"}) {
            result.set(table, snapshot.path(table));
        }
        return result;
    }

    private JsonNode awaitAccepted(String endpoint, boolean lost) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode facts = facts(endpoint);
            if (count(facts, lost ? "ACK_GENERATED" : "VENUE_ACCEPTED") == 1) return facts;
            Thread.sleep(20);
        }
        throw new AssertionError("venue fault barrier not reached");
    }
    private int count(JsonNode facts, String type) {
        int result = 0;
        for (JsonNode event : facts.path("events")) if (type.equals(event.path("type").asText())) result++;
        return result;
    }
    private JsonNode event(JsonNode facts, String type) {
        for (JsonNode event : facts.path("events")) if (type.equals(event.path("type").asText())) return event;
        throw new AssertionError("missing venue event " + type);
    }
    private JsonNode facts(String endpoint) throws Exception {
        return mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    private void control(String endpoint, String command) throws Exception {
        assertEquals(200, http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control"))
                .timeout(Duration.ofSeconds(3)).POST(HttpRequest.BodyPublishers.ofString(command)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    private String value(Connection connection, String sql) throws Exception {
        try (var query = connection.createStatement(); var result = query.executeQuery(sql)) {
            assertTrue(result.next()); return result.getString(1);
        }
    }
}
