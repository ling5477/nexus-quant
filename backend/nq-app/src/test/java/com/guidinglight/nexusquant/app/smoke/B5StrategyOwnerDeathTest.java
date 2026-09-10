package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 实际 scanOnce 的 durable busy 门禁必须在 owner 死亡且 Order 已证明未发送后可恢复。 */
@EnabledIfSystemProperty(named = "nq.b5.resume", matches = "true")
class B5StrategyOwnerDeathTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void deadDispatchMustNotPermanentlyBlockScheduleAfterNoSendFinality() throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/b5-strategy-death/" + UUID.randomUUID());
        Files.createDirectories(dir);
        System.out.println("B5_STRATEGY_ROOT " + dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", "STRATEGY_SCAN_OWNER_DEATH_BEFORE_AUTHORITY")
                .put("controllerPid", ProcessHandle.current().pid());
        try {
            try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env);
                // 全部业务种子在 NQ 启动前；固定一条已到期计划，运行时不推进时间/lastTriggeredAt 或改 busy 状态。
                try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
                    s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot) "
                            + "SELECT 'b5-strategy','b5-strategy','B5 scheduler fixture','TEST','oKx',account_id,'SIM',true,"
                            + "'{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"10\"}'::jsonb FROM accounts WHERE account_code='b0-account'");
                    s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,enabled,dedup_scope,exchange_code,account_id,trade_env,created_at) "
                            + "SELECT 'b5','b5-strategy','0 * * * * *','UTC',true,'SCHEDULE_WINDOW','oKx',account_id,'SIM',CURRENT_TIMESTAMP-INTERVAL '2 minutes' FROM accounts WHERE account_code='b0-account'");
                }
                proof.put("database", fixture.name()).put("venuePid", venue.process.pid());
                try (var reader = fixture.checker(); var a = new B0Processes.Child(B0NqProcessMain.class, dir, "nq-a", env).awaitReady();
                     var b = new B0Processes.Child(B0NqProcessMain.class, dir, "nq-b", env).awaitReady()) {
                    proof.put("nqPid", a.process.pid()).put("restartPid", b.process.pid());
                    assertNotEquals(a.process.pid(), b.process.pid());
                    proof.put("postgres", value(reader, "SHOW server_version"));
                    assertTrue(proof.path("postgres").asText().startsWith("16."));
                    assertEquals("50", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                    a.send("ARM_B5_PRE_SEND"); a.send("BEGIN_B5_STRATEGY");
                    long until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                    while (!Files.readString(a.log).contains("B5_CUT PRE_SEND")) {
                        assertTrue(a.process.isAlive()); assertTrue(System.nanoTime() < until, "BLOCKED / B5_HARNESS_CONTROL_GAP"); Thread.sleep(20);
                    }
                    assertEquals("DISPATCHING", value(reader, "SELECT status FROM strategy_runs"));
                    assertEquals("SENT", value(reader, "SELECT status FROM orders"));
                    assertEquals("OKX", value(reader, "SELECT venue FROM orders"));
                    assertEquals("NOT_ARMED", value(reader, "SELECT state FROM ordinary_place_authorities"));
                    proof.set("atCut", snapshot(reader));
                    a.kill(); proof.put("oldProcessDeadBeforeRecovery", true);
                    b.send("B5_RECOVERY"); b.send("RECOVER");
                    assertEquals("CANCELLED", value(reader, "SELECT status FROM orders"));
                    assertEquals("REVOKED_BEFORE_SEND", value(reader, "SELECT state FROM ordinary_place_authorities"));
                    proof.set("afterRecovery", snapshot(reader));
                    String retry = b.send("RUN_B5_STRATEGY");
                    proof.put("successorSkippedBusy", retry.contains("strategy_run_active"));
                    b.close();
                    try (var c = new B0Processes.Child(B0NqProcessMain.class, dir, "nq-restart", env).awaitReady()) {
                        proof.put("successorPid", c.process.pid());
                        c.send("B5_RECOVERY"); c.send("RECOVER");
                        String afterRestart = c.send("RUN_B5_STRATEGY");
                        proof.put("restartSkippedBusy", afterRestart.contains("strategy_run_active"));
                        proof.set("final", snapshot(reader));
                    }
                    var facts = mapper.readTree(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                            .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
                    proof.set("venue", facts);
                    assertEquals(0, facts.path("placeRequests").asInt());
                    assertEquals(0, facts.path("cancels").asInt());
                    assertEquals("0", value(reader, "SELECT count(*) FROM trades"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM execution_intents"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM execution_receipts"));
                }
            }
            proof.put("cleanup", true);
            assertFalse(proof.path("successorSkippedBusy").asBoolean() && proof.path("restartSkippedBusy").asBoolean(),
                    "FAIL / L4_B5_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED: dead DISPATCHING run permanently blocks reachable schedule after V49 no-send finality");
            proof.put("result", "PASS");
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName()); throw failure;
        } finally {
            Path raw = dir.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-R", 10);
        }
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : List.of("strategy_schedules", "strategy_runs", "orders", "ordinary_place_authorities", "trades", "event_store", "ledger_entries", "audit_logs", "execution_intents", "execution_receipts"))
            result.set(table, mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM " + table + " t")));
        return result;
    }
    private static String value(Connection reader, String sql) throws Exception {
        try (var s = reader.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
}
