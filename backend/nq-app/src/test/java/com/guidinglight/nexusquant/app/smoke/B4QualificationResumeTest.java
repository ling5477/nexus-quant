package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** B4剩余矩阵：PG实际拒绝/断连/成功响应丢失与真实进程死亡，不手工修复业务事实。 */
@EnabledIfSystemProperty(named = "nq.b4.resume", matches = "true")
class B4QualificationResumeTest {
    enum Row {
        HEALTHY("NONE", "NONE"),
        PREPARE_ROLLBACK("PREPARE", "ROLLBACK"),
        PREPARE_REJECT("PREPARE", "REJECT"),
        PREPARE_CONNECTION_LOSS("PREPARE", "BEFORE_DROP"),
        ACK_REJECT("ACK", "REJECT"),
        TRADE_REJECT("TRADE", "REJECT"),
        ACK_RESPONSE_LOSS("ACK", "AFTER_DROP"),
        TRADE_RESPONSE_LOSS("TRADE", "AFTER_DROP"),
        LEDGER_RESPONSE_LOSS("LEDGER", "AFTER_DROP"),
        LEDGER_CONNECTION_LOSS("LEDGER", "BEFORE_DROP"),
        ACK_DEATH_BEFORE_COMMIT("ACK", "BEFORE_PAUSE"),
        LEDGER_DEATH_BEFORE_COMMIT("LEDGER", "BEFORE_PAUSE"),
        ACK_DEATH_AFTER_COMMIT("ACK", "AFTER_PAUSE"),
        CANCELLED_TRADE_DEATH("TRADE", "BEFORE_PAUSE");
        final String target;
        final String fault;
        Row(String target, String fault) { this.target = target; this.fault = fault; }
        boolean wire() { return fault.startsWith("BEFORE") || fault.startsWith("AFTER"); }
        boolean committed() { return fault.startsWith("AFTER"); }
        boolean prepare() { return target.equals("PREPARE"); }
        boolean cancelled() { return this == CANCELLED_TRADE_DEATH; }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final B2RealProcessProofTest accounting = new B2RealProcessProofTest();

    @Test void remainingFailureMatrixConvergesWithoutResendingPlace() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b4-qualification-resume/" + UUID.randomUUID());
        Files.createDirectories(root);
        System.out.println("B4_RESUME_ROOT " + root);
        int run = 0;
        try (var pg = B0Processes.Pg.start()) {
            for (String environment : new String[]{"SIM", "LIVE"}) for (Row row : Row.values()) {
                var excluded = List.of(System.getProperty("nq.b4.resume.exclude", "").split(","));
                if (excluded.contains(row.name()) || excluded.contains(environment + ":" + row.name())) continue;
                if (!System.getProperty("nq.b4.resume.row", "ALL").equals("ALL")
                        && !row.name().equals(System.getProperty("nq.b4.resume.row"))) continue;
                execute(pg, root.resolve(environment + "-" + row.name()), row, environment, ++run);
            }
        }
        assertTrue(run > 0, "B4 row selection must execute at least one scenario");
    }

    private void execute(B0Processes.Pg pg, Path directory, Row row, String environment, int run) throws Exception {
        Files.createDirectories(directory);
        ObjectNode proof = mapper.createObjectNode().put("row", row.name()).put("target", row.target)
                .put("fault", row.fault).put("environment", environment).put("controllerPid", ProcessHandle.current().pid());
        try {
            String database;
            try (var fixture = B0Fixture.create(pg)) {
                database = fixture.name();
                B4TransactionFaults.installDeferredRejection(fixture);
                var proxyEnv = B0Processes.cleanEnvironment(); proxyEnv.put("NQ_B4_DB", fixture.url());
                try (var proxy = new B0Processes.Child(B4PgWireProxyMain.class, directory, "pg-wire", proxyEnv);
                     var venue = new B0Processes.Child(B2SyntheticVenueMain.class, directory, "venue", B0Processes.cleanEnvironment())) {
                    String proxied = fixture.url().replaceFirst("127\\.0\\.0\\.1:[0-9]+", "127.0.0.1:" + proxy.ready());
                    String endpoint = "http://127.0.0.1:" + venue.ready();
                    var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", fixture.url());
                    env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                    fixture.initialize(true, endpoint, env, true);
                    var firstEnv = new LinkedHashMap<>(env); firstEnv.put("NQ_B0_DB", proxied);
                    proof.put("database", database).put("venuePid", venue.process.pid()).put("proxyPid", proxy.process.pid());
                    try (var reader = fixture.checker();
                         var nq = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-a", firstEnv)) {
                        nq.ready(); proof.put("nqPid", nq.process.pid());
                        proof.put("postgres", value(reader, "SHOW server_version")).put("schema", value(reader,
                                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                        assertTrue(proof.path("postgres").asText().startsWith("16.")); assertEquals("50", proof.path("schema").asText());
                        assertNotEquals(nq.process.pid(), venue.process.pid());
                        String place = environment.equals("SIM") ? "PLACE_B2" : "PLACE_B2_LIVE";
                        if (row == Row.HEALTHY) {
                            assertTrue(nq.send(place).endsWith("ACCEPTED")); control(endpoint, "FILL 10 0.01");
                            assertEquals("RECOVER 1", nq.send("RECOVER"));
                        } else {
                            if (row.target.equals("TRADE") || row.target.equals("LEDGER")) {
                                assertTrue(nq.send(place).endsWith("ACCEPTED"));
                                control(endpoint, row.cancelled() ? "FILL 4 0.01" : "FILL 10 0.01");
                                if (row.cancelled()) assertTrue(nq.send("CANCEL").endsWith("CANCELLED"));
                            }
                            String mode = row.wire() ? "WIRE" : row.fault;
                            assertEquals("ARMED " + row.target + " " + mode, nq.send("ARM_B4_TX " + row.target + " " + mode));
                            if (row.wire()) assertEquals("ARMED " + row.fault, proxy.send("ARM " + row.fault));
                            nq.startCommand(row.prepare() || row.target.equals("ACK") ? place : "RECOVER");
                            if (row.wire()) {
                                String marker = "B4_WIRE_CUT " + row.fault + " " + (row.committed()
                                        ? "SERVER_COMMIT_CONFIRMED_RESPONSE_WITHHELD" : "COMMIT_NOT_FORWARDED");
                                awaitLog(proxy, marker); proof.put("wireBoundary", marker);
                                if (row.fault.endsWith("PAUSE")) assertTrue(nq.process.isAlive(), "must kill a still-running NQ");
                                proof.set("beforeKill", snapshot(reader));
                            } else {
                                assertTrue(nq.process.waitFor(25, TimeUnit.SECONDS), "fault must leave caller without success");
                                String log = Files.readString(nq.log);
                                assertTrue(log.contains(row.fault.equals("REJECT") ? "B4_DEFERRED_COMMIT_REJECTION" : "B4_EXPLICIT_ROLLBACK_BEFORE_COMMIT"));
                            }
                        }
                        if (nq.process.isAlive()) nq.kill();
                        if (row.wire()) assertEquals("DROPPED", proxy.send("DROP_PENDING"));
                        // 等待PG处理连接EOF；不把网络关闭瞬间尚未完成的abort当作最终结果。
                        awaitNoApplicationTransaction(reader);
                        ObjectNode cut = snapshot(reader); proof.set("atCut", cut);
                        assertCut(row, cut);
                        proof.put("actualCommitOutcome", row == Row.HEALTHY || row.committed() ? "COMMITTED"
                                : row.fault.equals("ROLLBACK") ? "ROLLED_BACK" : row.fault.equals("REJECT")
                                ? "COMMIT_DEFINITIVELY_FAILED" : "NOT_COMMITTED_CONNECTION_CLOSED");
                        proof.put("applicationCommitOutcome", row.committed() ? "COMMIT_OUTCOME_AMBIGUOUS"
                                : row.fault.equals("REJECT") ? "COMMIT_DEFINITIVELY_FAILED" : row.fault);
                        JsonNode cutVenue = facts(endpoint); proof.set("venueAtCut", cutVenue);
                        assertEquals(row.prepare() ? 0 : 1, cutVenue.path("placeRequests").asInt());
                        if (row.target.equals("ACK")) control(endpoint, "FILL 10 0.01");
                        try (var restarted = new B0Processes.Child(B0NqProcessMain.class, directory, "nq-b", env)) {
                            restarted.ready(); proof.put("restartPid", restarted.process.pid());
                            assertNotEquals(nq.process.pid(), restarted.process.pid());
                            assertTrue(restarted.send("ENGAGE").startsWith("ENGAGE ENGAGED"));
                            assertEquals("ENGAGED", value(reader, "SELECT status FROM kill_switch_states"));
                            assertTrue(restarted.send("RECOVER").startsWith("RECOVER "));
                            JsonNode truth = facts(endpoint);
                            if (!row.prepare()) accounting.assertAccounting(reader, truth);
                            ObjectNode recovered = snapshot(reader); proof.set("afterRecovery", recovered);
                            assertFinal(row, cut, recovered, environment);
                            for (int i = 0; i < 3; i++) assertEquals("RECOVER 0", restarted.send("RECOVER"));
                            ObjectNode replay = snapshot(reader);
                            assertEquals(recovered, replay); proof.set("afterReplay", replay);
                            // 响应丢失时已提交的原子集合必须原样存在，而不是通过重做形成新ID/version。
                            if (row.committed() && row.target.equals("TRADE")) {
                                assertEquals(cut.path("trades"), recovered.path("trades"));
                                assertEquals(cut.path("tradeEvents"), recovered.path("tradeEvents"));
                            }
                            if (row.committed() && row.target.equals("LEDGER")) {
                                for (String field : new String[]{"trades", "tradeEvents", "ledger_entries", "ledger_events", "positions", "account_snapshots"})
                                    assertEquals(cut.path(field), recovered.path(field));
                            }
                            // ESDB-00在prepare回滚后重放同一client；其他行用新client验证Kill拒绝新mutation。
                            assertTrue(restarted.send(row.prepare() ? place : "PLACE_B3_NEW").endsWith(" RISK_REJECTED"));
                            assertEquals("ENGAGED", value(reader, "SELECT status FROM kill_switch_states"));
                            proof.put("newPlaceUnderKill", "RISK_REJECTED");
                            if (row.prepare()) {
                                assertEquals("1", value(reader, "SELECT count(DISTINCT payload_json->'payload'->>'client_order_id') FROM event_store WHERE event_type='PlaceOrderCommand'"));
                                assertEquals("RISK_REJECTED", value(reader, "SELECT status FROM orders"));
                                proof.put("sameClientReplayUnderKill", true).set("afterKillProbe", snapshot(reader));
                            }
                        }
                        proof.set("venue", facts(endpoint));
                        assertEquals(row.prepare() ? 0 : 1, proof.path("venue").path("placeRequests").asInt());
                        assertEquals(row.prepare() ? 0 : 1, proof.path("venue").path("places").asInt());
                        assertEquals(row.cancelled() ? 1 : 0, proof.path("venue").path("cancels").asInt());
                        proof.put("blindRetries", 0).put("result", "PASS");
                    }
                }
            }
            assertTrue(pg.databaseAbsent(database)); proof.put("cleanup", true);
            System.out.println("B4_RESUME_PASS " + environment + " " + row.name());
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Path raw = directory.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B4-R2", run);
        }
    }

    private void assertCut(Row row, JsonNode cut) {
        if (row.prepare()) {
            for (String key : new String[]{"orders", "trades", "tradeEvents", "ledger_entries", "ledger_events", "positions", "account_snapshots", "risk_events"}) assertEquals(0, cut.path(key).size(), key);
            assertEquals(1, cut.path("commandCount").asInt()); assertEquals(0, cut.path("orderEventCount").asInt());
        } else if (row.target.equals("ACK")) {
            assertEquals(row.committed() ? "ACCEPTED" : "SENT", cut.path("orders").get(0).path("status").asText());
            assertEquals(row.committed() ? 3 : 2, cut.path("orders").get(0).path("version").asInt());
            assertEquals(row.committed() ? 1 : 0, cut.path("ackCount").asInt());
            assertEquals(0, cut.path("trades").size());
        } else {
            int trades = row.target.equals("TRADE") && !row.committed() ? 0 : 1;
            assertEquals(trades, cut.path("trades").size()); assertEquals(trades, cut.path("tradeEvents").size());
            int ledger = row == Row.HEALTHY || row.target.equals("LEDGER") && row.committed() ? 4 : 0;
            assertEquals(ledger, cut.path("ledger_entries").size()); assertEquals(ledger, cut.path("ledger_events").size());
            if (ledger == 0) { assertEquals(0, cut.path("positions").size()); assertEquals(0, cut.path("account_snapshots").size()); }
        }
    }

    private void assertFinal(Row row, JsonNode cut, JsonNode result, String environment) {
        if (row.prepare()) { assertCut(row, result); return; }
        assertEquals(1, result.path("orders").size());
        assertEquals(row.cancelled() ? "CANCELLED" : "FILLED", result.path("orders").get(0).path("status").asText());
        // ACK未提交时由SENT直接查询收敛到FILLED，只增加一次version；不能虚构一次ACK推进。
        int expectedVersion = cut.path("orders").get(0).path("version").asInt() + (row.target.equals("ACK") ? 1 : 0);
        assertEquals(expectedVersion, result.path("orders").get(0).path("version").asInt());
        assertEquals(cut.path("orders").get(0).path("order_id"), result.path("orders").get(0).path("order_id"));
        assertEquals(1, result.path("trades").size()); assertEquals(1, result.path("tradeEvents").size());
        assertEquals(4, result.path("ledger_entries").size()); assertEquals(4, result.path("ledger_events").size());
        assertEquals(environment, result.path("trades").get(0).path("trade_env").asText());
        assertEquals(environment, result.path("tradeEvents").get(0).path("payload_json").path("payload").path("trade_env").asText());
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = accounting.snapshot(reader);
        result.set("tradeEvents", mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(e) ORDER BY event_id)::text,'[]') FROM event_store e WHERE event_type='TradeExecuted'")));
        result.put("commandCount", Integer.parseInt(value(reader, "SELECT count(*) FROM event_store WHERE event_type='PlaceOrderCommand'")));
        result.put("orderEventCount", Integer.parseInt(value(reader, "SELECT count(*) FROM event_store WHERE topic='order.event.v1'")));
        result.put("ackCount", Integer.parseInt(value(reader, "SELECT count(*) FROM event_store WHERE event_type='OrderAck'")));
        return result;
    }
    private void awaitNoApplicationTransaction(Connection reader) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!value(reader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND usename='nq_b0_app'").equals("0")) {
            assertTrue(System.nanoTime() < deadline, "application backend did not exit"); Thread.sleep(20);
        }
    }
    private void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains(marker)) {
            assertTrue(child.process.isAlive(), "wire process exited");
            assertTrue(System.nanoTime() < deadline, "BLOCKED / B4_HARNESS_CONTROL_GAP: " + marker); Thread.sleep(20);
        }
    }
    private String value(Connection reader, String sql) throws Exception {
        try (var statement = reader.createStatement(); var rows = statement.executeQuery(sql)) { assertTrue(rows.next()); return rows.getString(1); }
    }
    private JsonNode facts(String endpoint) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return mapper.readTree(response.body());
    }
    private void control(String endpoint, String command) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
