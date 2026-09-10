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
import java.time.Duration;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 顺序运行真实时序矩阵；首个失败退出，Controller 只读业务表。 */
@EnabledIfSystemProperty(named = "nq.b2", matches = "true")
class B2RealProcessProofTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private enum Scenario { ZERO_CANCEL, PARTIAL_BEFORE_CANCEL, FILL_DURING_CANCEL,
        LATE_FULL, STALE_CANCEL, STALE_PLACE, RESTART_FULL, MULTI_PARTIAL }

    @Test void v49AffectedOccAndPerFillRegression() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b2-v49/" + UUID.randomUUID());
        Files.createDirectories(root);
        System.out.println("B2_V49_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            for (String environment : List.of("SIM", "LIVE")) {
                scenario(pg, root.resolve(environment + "-STALE_PLACE"), Scenario.STALE_PLACE, environment, 1);
                scenario(pg, root.resolve(environment + "-MULTI_PARTIAL"), Scenario.MULTI_PARTIAL, environment, 1);
            }
        }
    }

    @Test void provesCompleteCancelFillOrderingMatrix() throws Exception {
        Path directory = B0Processes.root().resolve("backend/nq-app/target/b2-qualification/" + UUID.randomUUID());
        Files.createDirectories(directory);
        System.out.println("B2_MATRIX_CONTROLLER pid=" + ProcessHandle.current().pid() + " evidence=" + directory);
        try (var postgres = B0Processes.Pg.start()) {
            for (String environment : List.of("SIM", "LIVE")) {
                for (int repeat = 1; repeat <= 3; repeat++) {
                    for (Scenario scenario : Scenario.values()) {
                        scenario(postgres, directory.resolve(environment + "-" + scenario + "-" + repeat), scenario, environment, repeat);
                    }
                }
            }
        }
    }

    private void scenario(B0Processes.Pg postgres, Path directory, Scenario scenario,
                          String environment, int repeat) throws Exception {
        Files.createDirectories(directory);
        boolean full = Set.of(Scenario.LATE_FULL, Scenario.STALE_CANCEL, Scenario.STALE_PLACE, Scenario.RESTART_FULL).contains(scenario);
        String executed = full ? "10" : scenario == Scenario.ZERO_CANCEL ? "0" : scenario == Scenario.MULTI_PARTIAL ? "6" : "4";
        var proof = mapper.createObjectNode().put("scenario", scenario.name()).put("repeat", repeat)
                .put("controllerPid", ProcessHandle.current().pid()).put("orderEnvironment", environment);
        String database;
        try (var venue = new B0Processes.Child(B2SyntheticVenueMain.class, directory, "venue", B0Processes.cleanEnvironment());
             var fixture = B0Fixture.create(postgres)) {
            database = fixture.name();
            String endpoint = "http://127.0.0.1:" + venue.ready();
            proof.put("database", database).put("venuePid", venue.process.pid()).put("venueEndpoint", endpoint);
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env);
            try (var reader = fixture.checker(); var nq = new B0Processes.Child(B0NqProcessMain.class, directory, "nq", env)) {
                nq.ready(); proof.put("nqPid", nq.process.pid());
                assertNotEquals(nq.process.pid(), venue.process.pid());
                assertNotEquals(ProcessHandle.current().pid(), nq.process.pid());
                assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                assertEquals(B0Fixture.READER, value(reader, "SELECT current_user"));
                proof.put("postgres", value(reader, "SHOW server_version")).put("schema", "V50");
                assertTrue(proof.path("postgres").asText().startsWith("16."));
                String place = "LIVE".equals(environment) ? "PLACE_B2_LIVE" : "PLACE_B2";
                if (scenario == Scenario.STALE_PLACE) {
                    control(endpoint, "HOLD_PLACE");
                    assertEquals("BEGIN " + place, nq.send("BEGIN_" + place));
                    awaitEvent(endpoint, "PLACE_ACCEPTED");
                    proof.set("placePending", snapshot(reader));
                    fill(endpoint, "4", "0"); fill(endpoint, "3", "0.01"); fill(endpoint, "3", "0.02");
                    assertEquals("RECOVER 3", nq.send("RECOVER"));
                    assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                    proof.set("beforeStaleAck", snapshot(reader));
                    control(endpoint, "RELEASE_PLACE");
                    assertTrue(nq.send("AWAIT").endsWith("FILLED"));
                    proof.set("afterStaleAck", snapshot(reader));
                    assertEquals(proof.path("beforeStaleAck"), proof.path("afterStaleAck"));
                } else {
                    assertTrue(nq.send(place).endsWith("ACCEPTED"));
                    proof.set("zeroFill", snapshot(reader));
                    assertQuantity(reader, "0");
                    if (scenario != Scenario.ZERO_CANCEL && scenario != Scenario.FILL_DURING_CANCEL) {
                        if (scenario == Scenario.MULTI_PARTIAL) {
                            fill(endpoint, "2", "0"); fill(endpoint, "3", "0.01"); fill(endpoint, "1", "0.02");
                            assertEquals("RECOVER 3", nq.send("RECOVER"));
                            assertQuantity(reader, "6");
                        } else {
                            fill(endpoint, "4", "0"); assertEquals("RECOVER 1", nq.send("RECOVER"));
                            assertQuantity(reader, "4");
                        }
                        proof.set("partial", snapshot(reader));
                    }
                    if (scenario == Scenario.FILL_DURING_CANCEL || scenario == Scenario.STALE_CANCEL) {
                        control(endpoint, "HOLD_CANCEL");
                        assertEquals("BEGIN CANCEL", nq.send("BEGIN_CANCEL"));
                        awaitEvent(endpoint, "CANCEL_REQUEST_RECEIVED");
                        assertEquals("CANCEL_REQUESTED", value(reader, "SELECT status FROM orders"));
                        proof.set("cancelPending", snapshot(reader));
                        if (scenario == Scenario.FILL_DURING_CANCEL) fill(endpoint, "4", "0.01");
                        else {
                            fill(endpoint, "3", "0.01"); fill(endpoint, "3", "0.02");
                            assertEquals("RECOVER 2", nq.send("RECOVER"));
                            assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                            proof.set("beforeStaleAck", snapshot(reader));
                        }
                        control(endpoint, "RELEASE_CANCEL");
                        assertTrue(nq.send("AWAIT").endsWith(scenario == Scenario.STALE_CANCEL ? "FILLED" : "CANCELLED"));
                        if (scenario == Scenario.STALE_CANCEL) {
                            proof.set("afterStaleAck", snapshot(reader));
                            assertEquals(proof.path("beforeStaleAck"), proof.path("afterStaleAck"));
                        }
                    } else assertTrue(nq.send("CANCEL").endsWith("CANCELLED"));
                    proof.set("afterCancelAck", snapshot(reader));
                    if (scenario == Scenario.LATE_FULL || scenario == Scenario.RESTART_FULL) {
                        assertEquals("CANCELLED", value(reader, "SELECT status FROM orders"));
                        if (scenario == Scenario.RESTART_FULL) {
                            nq.kill(); assertFalse(nq.process.isAlive()); proof.put("oldProcessDeadBeforeLateFills", true);
                        }
                        fill(endpoint, "3", "0.01"); fill(endpoint, "3", "0.02");
                    }
                    control(endpoint, "CANCEL_EFFECT");
                }
                control(endpoint, "DUPLICATE_REPORTS");
                proof.set("venueBeforeRecovery", facts(endpoint));
                if (scenario == Scenario.RESTART_FULL) {
                    try (var second = new B0Processes.Child(B0NqProcessMain.class, directory, "restart-nq", env)) {
                        second.ready(); proof.put("restartPid", second.process.pid());
                        assertNotEquals(nq.process.pid(), second.process.pid());
                        recoverAndReplay(second, reader, proof, full, executed);
                    }
                } else recoverAndReplay(nq, reader, proof, full, executed);
                proof.set("finalDb", snapshot(reader)); proof.set("finalVenue", facts(endpoint));
                assertEquals(full ? "filled" : "canceled", proof.path("finalVenue").path("order").path("state").asText());
                assertEquals(1, proof.path("finalVenue").path("places").asInt());
                assertEquals(scenario == Scenario.STALE_PLACE ? 0 : 1, proof.path("finalVenue").path("cancels").asInt());
                assertEquals(environment, value(reader, "SELECT trade_env FROM orders"));
                assertEquals("0", value(reader, "SELECT count(*) FROM trades t JOIN orders o USING(order_id) WHERE t.trade_env<>o.trade_env"));
                assertEquals(proof.path("finalVenue").path("fills").size(), Integer.parseInt(value(reader, "SELECT count(*) FROM trades")));
                assertAccounting(reader, proof.path("finalVenue"));
                boolean correction = scenario == Scenario.LATE_FULL || scenario == Scenario.RESTART_FULL;
                assertEquals(correction ? "1" : "0", value(reader, "SELECT count(*) FROM audit_logs WHERE action='ORDER_TERMINAL_EXECUTION_CORRECTED'"));
                if (correction) {
                    long before = proof.path("afterCancelAck").path("orders").get(0).path("version").asLong();
                    assertEquals(before + 1, Long.parseLong(value(reader, "SELECT version FROM orders")));
                }
                assertOrderings(proof.path("finalVenue"), scenario);
                proof.put("executedQty", executed).put("remainingQty", new BigDecimal("10").subtract(new BigDecimal(executed)).toPlainString());
                proof.put("result", "PASS");
                Files.writeString(directory.resolve("raw-proof.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
                SyntheticEvidenceExport.write(directory.resolve("raw-proof.json"), "B2",
                        ("LIVE".equals(environment) ? 24 : 0) + (repeat - 1) * 8 + scenario.ordinal() + 1);
                System.out.println("B2_SCENARIO_PASS " + environment + " " + scenario + " repeat=" + repeat);
            }
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failureType", failure.getClass().getName()).put("failure", String.valueOf(failure.getMessage()));
            Files.writeString(directory.resolve("raw-failed-proof.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(directory.resolve("raw-failed-proof.json"), "B2",
                    ("LIVE".equals(environment) ? 24 : 0) + (repeat - 1) * 8 + scenario.ordinal() + 1);
            throw failure;
        }
        assertTrue(postgres.databaseAbsent(database));
    }

    private void recoverAndReplay(B0Processes.Child nq, Connection reader, ObjectNode proof, boolean full, String executed) throws Exception {
        proof.put("recoveryResult", nq.send("RECOVER"));
        assertEquals(full ? "FILLED" : "CANCELLED", value(reader, "SELECT status FROM orders"));
        assertQuantity(reader, executed);
        proof.set("afterRecovery", snapshot(reader));
        assertEquals("RECOVER 0", nq.send("RECOVER"));
        proof.set("afterReplay", snapshot(reader));
        assertEquals(proof.path("afterRecovery"), proof.path("afterReplay"));
    }

    private void assertQuantity(Connection reader, String executed) throws Exception {
        assertDecimal(executed, value(reader, "SELECT coalesce(sum(qty),0) FROM trades"));
        assertDecimal(new BigDecimal("10").subtract(new BigDecimal(executed)).toPlainString(),
                value(reader, "SELECT qty-(SELECT coalesce(sum(qty),0) FROM trades) FROM orders"));
    }

    private void fill(String endpoint, String qty, String fee) throws Exception { control(endpoint, "FILL " + qty + " " + fee); }

    private void awaitEvent(String endpoint, String type) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (sequence(facts(endpoint), type, false) == 0) {
            assertTrue(System.nanoTime() < deadline, "venue barrier not reached: " + type);
            Thread.sleep(10);
        }
    }

    private long sequence(JsonNode venue, String type, boolean last) {
        long found = 0;
        for (JsonNode event : venue.path("events")) if (type.equals(event.path("type").asText())) {
            found = event.path("sequence").asLong(); if (!last) return found;
        }
        return found;
    }

    private void assertOrderings(JsonNode venue, Scenario scenario) {
        long fill = sequence(venue, "FILL", false), lastFill = sequence(venue, "FILL", true);
        long cancel = sequence(venue, "CANCEL_REQUEST_RECEIVED", false), ack = sequence(venue, "CANCEL_ACK_GENERATED", false);
        if (scenario == Scenario.STALE_PLACE) {
            assertTrue(sequence(venue, "PLACE_ACCEPTED", false) < fill);
            assertTrue(lastFill < sequence(venue, "PLACE_ACK_GENERATED", false));
        } else {
            assertTrue(cancel > 0 && cancel < ack);
            assertTrue(ack < sequence(venue, "CANCEL_RESPONSE_DELIVERED", false));
            if (scenario == Scenario.FILL_DURING_CANCEL) assertTrue(cancel < fill && fill < ack);
            else if (scenario != Scenario.ZERO_CANCEL) assertTrue(fill < cancel);
            if (scenario == Scenario.LATE_FULL || scenario == Scenario.RESTART_FULL) assertTrue(ack < lastFill);
            if (scenario == Scenario.STALE_CANCEL) assertTrue(cancel < lastFill && lastFill < ack);
        }
    }

    void assertAccounting(Connection reader, JsonNode venue) throws Exception {
        // 根据每笔真实 fee 验证 canonical 成对分录，不固定每笔分录数量。
        for (JsonNode fill : venue.path("fills")) {
            String fillId = fill.path("tradeId").asText();
            String tradeId;
            try (var query = reader.prepareStatement("SELECT trade_id,price,qty,fee,fee_currency FROM trades WHERE exchange_trade_id=?")) {
                query.setString(1, fillId);
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next()); tradeId = rows.getString(1);
                    assertDecimal(fill.path("fillPx").asText(), rows.getBigDecimal(2).toPlainString());
                    assertDecimal(fill.path("fillSz").asText(), rows.getBigDecimal(3).toPlainString());
                    assertDecimal(new BigDecimal(fill.path("fee").asText()).abs().toPlainString(), rows.getBigDecimal(4).toPlainString());
                    assertEquals("USDT", rows.getString(5)); assertFalse(rows.next());
                }
            }
            var expected = new HashMap<String, BigDecimal>();
            BigDecimal amount = new BigDecimal(fill.path("fillPx").asText()).multiply(new BigDecimal(fill.path("fillSz").asText()));
            BigDecimal fee = new BigDecimal(fill.path("fee").asText()).abs();
            expected.put(tradeId + ":LEDGER:1", amount.negate()); expected.put(tradeId + ":LEDGER:2", amount);
            if (fee.signum() > 0) {
                expected.put(tradeId + ":LEDGER:FEE_1", fee.negate()); expected.put(tradeId + ":LEDGER:FEE_2", fee);
            }
            try (var query = reader.prepareStatement("SELECT idempotency_key,delta,currency,direction FROM ledger_entries WHERE ref_id=?")) {
                query.setString(1, tradeId);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        BigDecimal delta = expected.remove(rows.getString(1)); assertNotNull(delta);
                        assertDecimal(delta.toPlainString(), rows.getBigDecimal(2).toPlainString());
                        assertEquals("USDT", rows.getString(3));
                        assertEquals(delta.signum() < 0 ? "DEBIT" : "CREDIT", rows.getString(4));
                    }
                }
            }
            assertTrue(expected.isEmpty());
        }
        assertDecimal(venue.path("order").path("accFillSz").asText(), value(reader, "SELECT coalesce(sum(qty),0) FROM positions WHERE symbol='BTC-USDT'"));
        // 当前 canonical 成对本金/费用分录在同币种净额为零；持仓投影独立证明实际 BUY 数量。
        assertDecimal("0", value(reader, "SELECT coalesce(sum(delta),0) FROM ledger_entries WHERE currency='USDT'"));
        assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries WHERE currency<>'USDT'"));
        assertEquals(value(reader, "SELECT count(*) FROM ledger_entries"), value(reader, "SELECT count(*) FROM ledger_events"));
    }

    ObjectNode snapshot(Connection reader) throws Exception {
        var result = mapper.createObjectNode();
        reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        reader.setAutoCommit(false);
        try {
            for (String table : new String[]{"orders", "trades", "ledger_entries", "ledger_events", "positions",
                    "account_snapshots", "risk_events", "execution_intents", "execution_receipts"}) {
                result.set(table, mapper.readTree(value(reader,
                        "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
            }
            result.put("database", value(reader, "SELECT current_database()"));
            result.set("correction_audit", mapper.readTree(value(reader,
                    "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id)::text,'[]') FROM audit_logs t WHERE action='ORDER_TERMINAL_EXECUTION_CORRECTED'")));
            result.set("correction_events", mapper.readTree(value(reader,
                    "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY event_id)::text,'[]') FROM event_store t WHERE payload_json->'payload'->>'reason'='RECONCILE_FULL_EXECUTION_PROVEN'")));
            reader.commit();
        } finally { reader.rollback(); reader.setAutoCommit(true); }
        return result;
    }

    private JsonNode facts(String endpoint) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body());
    }

    private void control(String endpoint, String command) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    private String value(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getString(1);
        }
    }
    private void assertDecimal(String expected, String actual) { assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual))); }
}
