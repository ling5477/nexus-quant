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
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 保留实际生命周期缺口的红色断言；不得把永久 BUSY 改成允许结果来通过测试。 */
@EnabledIfSystemProperty(named = "nq.b5.lifecycle", matches = "true")
class B5DurableLifecycleCrashRecoveryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void createdOwnerDeathMustRecoverSameRunAndReleaseFutureWork() throws Exception {
        execute("CREATED", 1);
    }

    @Test void runningOwnerDeathAndDurableFillMustReleaseFutureWork() throws Exception {
        execute("RUNNING", 2);
    }

    private void execute(String cut, int run) throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/b5-lifecycle-crash/" + UUID.randomUUID());
        Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", cut)
                .put("controllerPid", ProcessHandle.current().pid());
        System.out.println("B5_LIFECYCLE_ROOT " + dir);
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url());
            env.put("NQ_B0_VENUE", endpoint);
            env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env);
            B5StrategyRunRecoveryProcessTest.seed(fixture);
            // 启动业务进程前一次性提交两个计划；运行期间不修改 run、Order、authority 或计划游标。
            try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var statement = owner.createStatement()) {
                statement.execute("UPDATE strategy_schedules SET cron_expr='* * * * * *', "
                        + "created_at=date_trunc('second',CURRENT_TIMESTAMP)-INTERVAL '2 seconds' WHERE schedule_job_id='b5'");
                statement.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,enabled,"
                        + "dedup_scope,exchange_code,account_id,trade_env,created_at) "
                        + "SELECT 'b5-future',strategy_id,'* * * * * *','UTC',true,dedup_scope,exchange_code,account_id,"
                        + "trade_env,date_trunc('second',CURRENT_TIMESTAMP)+INTERVAL '15 seconds' "
                        + "FROM strategy_schedules WHERE schedule_job_id='b5'");
            }
            try (var reader = fixture.checker();
                 var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady()) {
                proof.put("database", fixture.name()).put("postgres", value(reader, "SHOW server_version"))
                        .put("nqPid", a.process.pid()).put("venuePid", venue.process.pid());
                assertTrue(proof.path("postgres").asText().startsWith("16."));
                assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                if (cut.equals("CREATED")) {
                    a.send("ARM_B5_ADMISSION");
                    a.send("BEGIN_B5_STRATEGY");
                    awaitCut(a);
                    assertEquals("CREATED", value(reader, "SELECT status FROM strategy_runs"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                } else {
                    assertTrue(a.send("RUN_B5_STRATEGY").contains("outcome=TRIGGERED"));
                    assertEquals("RUNNING", value(reader, "SELECT status FROM strategy_runs"));
                    assertEquals("ACCEPTED", value(reader, "SELECT status FROM orders"));
                }
                String originalRun = value(reader, "SELECT strategy_run_id FROM strategy_runs");
                proof.set("atCut", snapshot(reader));
                a.kill();
                try (var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady()) {
                    proof.put("restartPid", b.process.pid());
                    if (cut.equals("RUNNING")) control(endpoint, "FILL 10 0.01");
                    b.send("B5_RECOVERY");
                    b.send("RECOVER");
                    b.send("RECOVER");
                    proof.put("explicitRecovery", b.send("RECOVER_B5_STRATEGY"));
                    if (cut.equals("CREATED") && facts(endpoint).path("placeRequests").asInt() > 0) {
                        control(endpoint, "FILL 10 0.01");
                        b.send("RECOVER");
                        b.send("RECOVER");
                        b.send("RECOVER_B5_STRATEGY");
                    }
                    proof.set("settledBeforeFuture", snapshot(reader));
                    proof.set("venueBeforeFuture", facts(endpoint));
                    assertTrue(proof.path("venueBeforeFuture").path("placeRequests").asInt() <= 1);
                    if (cut.equals("RUNNING")) {
                        assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                        assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                        assertEquals("4", value(reader, "SELECT count(*) FROM ledger_entries"));
                        assertEquals(1, proof.path("venueBeforeFuture").path("placeRequests").asInt());
                    }
                    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                    while (!"t".equals(value(reader, "SELECT bool_and(CURRENT_TIMESTAMP > "
                            + "greatest(created_at,last_triggered_at)+INTERVAL '1 second') FROM strategy_schedules"))) {
                        assertTrue(System.nanoTime() < deadline, "future schedule did not become due");
                        Thread.sleep(50);
                    }
                    proof.put("futureDue", true);
                    String scan = b.send("RUN_B5_STRATEGY");
                    proof.put("futureBusy", scan.contains("strategy_run_active"));
                    proof.put("futureNotDue", scan.contains("not_due"));
                    proof.put("futureOriginalTriggered", scan.contains(
                            "scheduleJobId=b5, strategyId=b5-strategy, outcome=TRIGGERED"));
                    proof.set("afterRecovery", snapshot(reader));
                }
                // 再启独立 JVM，排除原 JVM 的 busy set 和最后一次 callback 对结果的影响。
                try (var c = new B0Processes.Child(B0NqProcessMain.class, dir, "c", env).awaitReady()) {
                    proof.put("successorPid", c.process.pid());
                    proof.put("restartRecovery", c.send("RECOVER_B5_STRATEGY"));
                    proof.put("restartBusy", c.send("RUN_B5_STRATEGY").contains("strategy_run_active"));
                }
                proof.set("final", snapshot(reader));
                proof.set("venue", facts(endpoint));
                assertEquals("1", value(reader, "SELECT count(*) FROM strategy_runs WHERE admission_schedule_id='b5' "
                        + "AND admission_due_at=(SELECT admission_due_at FROM strategy_runs WHERE strategy_run_id='" + originalRun + "')"));
                assertEquals(originalRun, value(reader, "SELECT strategy_run_id FROM strategy_runs WHERE strategy_run_id='" + originalRun + "'"));
                assertEquals(0, proof.path("venue").path("cancels").asInt());
                // 新窗口允许合法的新订单；只有旧窗口的唯一性与旧 run 的释放是本回归的不变量。
                assertTrue(proof.path("futureOriginalTriggered").asBoolean(),
                        "STRATEGY_DURABLE_LIFECYCLE_RECOVERY_GAP: " + cut + " permanently blocks future windows");
                if (cut.equals("CREATED")) {
                    assertNotEquals("CREATED", value(reader, "SELECT status FROM strategy_runs WHERE strategy_run_id='" + originalRun + "'"));
                }
                proof.put("result", "PASS");
            }
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            Path raw = dir.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-LC", run);
        }
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : List.of("strategy_schedules", "strategy_runs", "orders", "ordinary_place_authorities",
                "b5_run_transitions", "trades", "ledger_entries", "event_store")) {
            result.set(table, mapper.readTree(value(reader,
                    "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        }
        return result;
    }

    private JsonNode facts(String endpoint) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        }
    }

    private void control(String endpoint, String command) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(endpoint + "/control"))
                    .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(command)).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    private static void awaitCut(B0Processes.Child child) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains("B5_CUT AFTER_ADMISSION")) {
            assertTrue(child.process.isAlive());
            assertTrue(System.nanoTime() < deadline, "admission boundary missing");
            Thread.sleep(20);
        }
    }

    private static String value(Connection reader, String sql) throws Exception {
        return B5StrategyRunRecoveryProcessTest.value(reader, sql);
    }
}
