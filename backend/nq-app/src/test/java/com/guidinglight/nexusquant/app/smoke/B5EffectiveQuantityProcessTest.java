package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 原始非步长整数倍数量必须经过真实 Spring/PG/adapter 后终结，不能只验证舍入算式。 */
@EnabledIfSystemProperty(named = "nq.b5.quantity", matches = "true")
class B5EffectiveQuantityProcessTest {
    @Test void unrepresentableVenueStepMustDurablyReject() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-effective-precision/" + UUID.randomUUID());
        System.out.println("B5_EFFECTIVE_PRECISION_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            matrixRow(pg, root.resolve("PRECISION"), "PRECISION", 1);
            matrixRow(pg, root.resolve("PRICE_PRECISION"), "PRICE_PRECISION", 2);
        }
    }

    @Test void normalizationAndCrashMatrix() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-effective-matrix/" + UUID.randomUUID());
        System.out.println("B5_EFFECTIVE_MATRIX_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            int index = 0;
            for (String scenario : List.of("EXACT", "ADJACENT", "ZERO", "BELOW_MIN", "PRICE_ZERO",
                    "CREATED", "PRE_ARM", "MAY", "HTTP", "RULE_CHANGE")) {
                matrixRow(pg, root.resolve(scenario), scenario, ++index);
            }
        }
    }

    private void matrixRow(B0Processes.Pg pg, Path dir, String scenario, int index) throws Exception {
        Files.createDirectories(dir); var mapper = new ObjectMapper();
        var proof = mapper.createObjectNode().put("scenario", scenario);
        String requested = switch (scenario) { case "EXACT" -> "10.000"; case "ADJACENT", "RULE_CHANGE" -> "10.0015";
            case "PRECISION" -> "10.00050001";
            case "ZERO" -> "0.0005"; case "BELOW_MIN" -> "0.0015"; default -> "10.0005"; };
        String effective = scenario.equals("ADJACENT") || scenario.equals("RULE_CHANGE") ? "10.001" : "10";
        String price = scenario.equals("PRICE_ZERO") ? "0.009" : scenario.equals("PRICE_PRECISION") ? "100.00000001" : "100.005";
        boolean invalid = List.of("ZERO", "BELOW_MIN", "PRICE_ZERO", "PRECISION", "PRICE_PRECISION").contains(scenario);
        boolean cut = List.of("CREATED", "PRE_ARM", "MAY", "HTTP", "RULE_CHANGE").contains(scenario);
        try (var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            if (scenario.equals("BELOW_MIN")) control(endpoint, "RULES 0.001 0.01 0.01");
            if (scenario.equals("PRECISION")) control(endpoint, "RULES 0.000000003 0.01 0.000000003");
            if (scenario.equals("PRICE_PRECISION")) control(endpoint, "RULES 0.001 0.000000003 0.001");
            var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env); B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
                s.execute("UPDATE strategy_definitions SET config_snapshot=jsonb_set(jsonb_set(config_snapshot,'{quantity}','\""
                        + requested + "\"'),'{price}','\"" + price + "\"')");
                if (scenario.equals("CREATED")) s.execute("UPDATE strategy_schedules SET cron_expr='* * * * * *',created_at=date_trunc('second',now())-INTERVAL '2 seconds'");
            }
            try (var reader = fixture.checker(); var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady()) {
                proof.put("database", fixture.name()).put("ownerPid", a.process.pid()).put("venuePid", venue.process.pid());
                if (cut) {
                    if (scenario.equals("HTTP")) control(endpoint, "HOLD_PLACE");
                    else a.send(scenario.equals("CREATED") ? "ARM_B5_ADMISSION" : scenario.equals("MAY") ? "ARM_B5_POST_ARM" : "ARM_V51_B");
                    a.send("BEGIN_B5_STRATEGY");
                    if (scenario.equals("HTTP")) {
                        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                        while (venueFacts(endpoint, mapper).path("places").asInt() != 1) { assertTrue(System.nanoTime() < deadline); Thread.sleep(20); }
                    } else awaitLog(a, "B5_CUT " + (scenario.equals("CREATED") ? "AFTER_ADMISSION" : scenario.equals("MAY") ? "POST_ARM" : "V51_B"));
                    proof.set("atCut", snapshot(reader, mapper));
                    if (!scenario.equals("CREATED")) {
                        assertDecimal(effective, value(reader, "SELECT effective_quantity FROM strategy_run_dispatch_work"));
                        assertDecimal(effective, value(reader, "SELECT qty FROM orders"));
                        assertEquals(scenario.equals("MAY") || scenario.equals("HTTP") ? "MAY_HAVE_ESCAPED" : "NOT_ARMED",
                                value(reader, "SELECT state FROM ordinary_place_authorities"));
                    }
                    a.kill();
                    if (scenario.equals("HTTP")) control(endpoint, "RELEASE_PLACE");
                    // 配置可变化，但它不再拥有已接纳 work；不改任何运行时业务事实。
                    try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
                        s.execute("UPDATE strategy_definitions SET config_snapshot=jsonb_set(config_snapshot,'{quantity}','\"99\"'),version=version+1");
                    }
                    if (scenario.equals("RULE_CHANGE")) control(endpoint, "RULES 0.01 0.1 0.01");
                } else {
                    assertTrue(a.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED")); a.kill();
                }
                try (var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady()) {
                    proof.put("successorPid", b.process.pid());
                    b.send("RECOVER_V51_ALL");
                    if (scenario.equals("MAY")) {
                        b.send("B5_RECOVERY"); b.send("RECOVER"); b.send("RECOVER_V51_ALL");
                        assertEquals("MAY_HAVE_ESCAPED", value(reader, "SELECT state FROM ordinary_place_authorities"));
                        assertEquals(0, venueFacts(endpoint, mapper).path("placeRequests").asInt());
                        assertEquals("RUNNING", value(reader, "SELECT status FROM strategy_runs"));
                    } else if (invalid || scenario.equals("RULE_CHANGE")) {
                        assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                        assertEquals(0, venueFacts(endpoint, mapper).path("placeRequests").asInt());
                        if (invalid) {
                            assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                            assertEquals("0", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                            assertEquals("1", value(reader, "SELECT count(*) FROM strategy_run_dispatch_work WHERE normalization_rejection IS NOT NULL"));
                            if (scenario.endsWith("PRECISION")) assertEquals("OKX_EFFECTIVE_PRECISION_UNSUPPORTED",
                                    value(reader, "SELECT normalization_rejection FROM strategy_run_dispatch_work"));
                        } else assertDecimal(effective, value(reader, "SELECT effective_quantity FROM strategy_run_dispatch_work"));
                    } else {
                        assertDecimal(effective, venueFacts(endpoint, mapper).path("order").path("sz").asText());
                        control(endpoint, "FILL " + effective + " 0.01"); b.send("RECOVER"); b.send("RECOVER"); b.send("RECOVER_V51_ALL");
                        assertEquals("SUCCEEDED", value(reader, "SELECT status FROM strategy_runs"));
                        assertDecimal(effective, value(reader, "SELECT qty FROM orders"));
                        assertDecimal(effective, value(reader, "SELECT sum(qty) FROM trades"));
                        assertDecimal("100", value(reader, "SELECT price FROM orders"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                        assertEquals("4", value(reader, "SELECT count(*) FROM ledger_entries"));
                        assertEquals(1, venueFacts(endpoint, mapper).path("placeRequests").asInt());
                    }
                    assertDecimal(requested, value(reader, "SELECT quantity FROM strategy_run_dispatch_work"));
                    var stable = snapshot(reader, mapper); b.send("RECOVER_V51_ALL"); assertEquals(stable, snapshot(reader, mapper));
                    assertEquals("1", value(reader, "SELECT count(*) FROM strategy_runs"));
                    proof.set("final", snapshot(reader, mapper)); proof.set("venue", venueFacts(endpoint, mapper)); proof.put("result", "PASS");
                    if (scenario.equals("CREATED")) {
                        b.send("ARM_B5_ADMISSION"); b.send("BEGIN_B5_STRATEGY"); awaitLog(b, "B5_CUT AFTER_ADMISSION");
                        assertEquals("2", value(reader, "SELECT count(*) FROM strategy_runs"));
                        assertEquals("2", value(reader, "SELECT count(DISTINCT admission_due_at) FROM strategy_runs"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                        assertEquals(1, venueFacts(endpoint, mapper).path("placeRequests").asInt());
                        proof.put("futureWindowAdmitted", true); b.kill();
                    }
                    System.out.println("B5_EFFECTIVE_MATRIX_PASS " + scenario);
                }
            }
        } catch (Exception | AssertionError failure) { proof.put("result", "FAIL"); throw failure; }
        finally { Path raw = dir.resolve("raw-proof.json"); Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof)); SyntheticEvidenceExport.write(raw, "B5-EQM", index); }
    }

    static void assertDecimal(String expected, String actual) { assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual))); }
    static JsonNode venueFacts(String endpoint, ObjectMapper mapper) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        }
    }
    static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!Files.readString(child.log).contains(marker)) { assertTrue(child.process.isAlive()); assertTrue(System.nanoTime() < deadline, marker); Thread.sleep(20); }
    }

    @Test void roundedFillMustRecoverAndReleaseNextWindow() throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/b5-effective-quantity/" + UUID.randomUUID());
        Files.createDirectories(dir);
        var mapper = new ObjectMapper();
        var proof = mapper.createObjectNode().put("scenario", "ROUNDED_RUNNING_OWNER_DEATH");
        System.out.println("B5_EFFECTIVE_ROOT " + dir);
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env); B5StrategyRunRecoveryProcessTest.seed(fixture);
            // 仅在业务进程启动前设置策略输入；运行后不伪造 run、Order、Trade 或 cursor。
            try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
                s.execute("UPDATE strategy_definitions SET config_snapshot=jsonb_set(config_snapshot,'{quantity}','\"10.0005\"')");
                s.execute("UPDATE strategy_schedules SET cron_expr='* * * * * *',created_at=date_trunc('second',now())-INTERVAL '2 seconds'");
            }
            try (var reader = fixture.checker(); var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady()) {
                assertTrue(value(reader, "SHOW server_version").startsWith("16."));
                assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                assertTrue(a.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                String run = value(reader, "SELECT strategy_run_id FROM strategy_runs");
                control(endpoint, "FILL 10 0.01");
                a.send("RECOVER"); a.send("RECOVER");
                assertEquals("RUNNING", value(reader, "SELECT status FROM strategy_runs"));
                assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                proof.put("database", fixture.name()).put("ownerPid", a.process.pid()).put("venuePid", venue.process.pid());
                proof.set("atDeath", snapshot(reader, mapper)); a.kill();
                try (var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady()) {
                    proof.put("successorPid", b.process.pid()); b.send("RECOVER_V51_ALL");
                    proof.set("afterRecovery", snapshot(reader, mapper));
                    assertEquals("SUCCEEDED", value(reader, "SELECT status FROM strategy_runs WHERE strategy_run_id='" + run + "'"),
                            "EFFECTIVE_EXECUTION_QUANTITY_TERMINALIZATION_GAP");
                    assertEquals("10.00050000", value(reader, "SELECT quantity FROM strategy_run_dispatch_work"));
                    assertEquals("10.00000000", value(reader, "SELECT qty FROM orders"));
                    assertEquals("10.00000000", value(reader, "SELECT sum(qty) FROM trades"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                    assertEquals("4", value(reader, "SELECT count(*) FROM ledger_entries"));
                    var stable = snapshot(reader, mapper); b.send("RECOVER"); b.send("RECOVER_V51_ALL");
                    assertEquals(stable, snapshot(reader, mapper));
                    try (var client = HttpClient.newHttpClient()) {
                        var facts = mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
                        assertEquals(1, facts.path("placeRequests").asInt());
                        assertEquals(0, new BigDecimal("10").compareTo(new BigDecimal(facts.path("order").path("sz").asText())));
                        proof.set("venue", facts);
                    }
                    // 未来窗口只观察 admission；保持本场景只有原订单的一次 PLACE。
                    b.send("ARM_B5_ADMISSION"); b.send("BEGIN_B5_STRATEGY");
                    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                    while (!Files.readString(b.log).contains("B5_CUT AFTER_ADMISSION")) {
                        assertTrue(System.nanoTime() < deadline, "future window remains blocked"); Thread.sleep(20);
                    }
                    assertEquals("2", value(reader, "SELECT count(*) FROM strategy_runs"));
                    assertEquals("2", value(reader, "SELECT count(DISTINCT admission_due_at) FROM strategy_runs"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM orders")); b.kill();
                }
                proof.put("result", "PASS");
            }
        } catch (Exception | AssertionError failure) { proof.put("result", "FAIL"); throw failure; }
        finally {
            Path raw = dir.resolve("raw-proof.json"); Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-EQ", 1);
        }
    }

    static String value(Connection c, String sql) throws Exception { return B5StrategyRunRecoveryProcessTest.value(c, sql); }
    static void control(String endpoint, String command) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
    static JsonNode snapshot(Connection c, ObjectMapper mapper) throws Exception {
        var result = mapper.createObjectNode();
        for (String table : List.of("strategy_runs", "strategy_run_dispatch_work", "orders", "ordinary_place_authorities", "trades", "ledger_entries"))
            result.set(table, mapper.readTree(value(c, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        return result;
    }
}
