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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 同一窗口的两个真实扫描都通过只读门禁后竞争；最终判定只使用数据库及独立 Venue。 */
@EnabledIfSystemProperty(named = "nq.b5.scan", matches = "true")
class B5StrategyScanConcurrencyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @RepeatedTest(3)
    void sameWindowAcrossJvmsMustCreateAtMostOneLogicalDispatch(RepetitionInfo repetition) throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/b5-scan-concurrency/" + UUID.randomUUID());
        Files.createDirectories(dir);
        System.out.println("B5_SCAN_ROOT " + dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", "SAME_WINDOW_CONCURRENT_STRATEGY_SCAN")
                .put("controllerPid", ProcessHandle.current().pid())
                .put("boundary", "AFTER_ACTIVE_AND_WINDOW_DEDUP_BEFORE_PRODUCTION_TRIGGER")
                .put("identityRule", "SCHEDULE_WINDOW: schedule_job_id + dueAt; client_order_id = coid- + request_id");
        try {
            try (var pg = B0Processes.Pg.start()) {
                String database;
                try (var fixture = B0Fixture.create(pg);
                     var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                    database = fixture.name();
                    String endpoint = "http://127.0.0.1:" + venue.ready();
                    var env = B0Processes.cleanEnvironment();
                    env.put("NQ_B0_DB", fixture.url());
                    env.put("NQ_B0_VENUE", endpoint);
                    env.put("NQ_B0_PROFILE", "b0-test");
                    fixture.initialize(true, endpoint, env);
                    B5StrategyRunRecoveryProcessTest.seed(fixture);
                    try (var reader = fixture.checker();
                         var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady();
                         var b = new B0Processes.Child(B0NqProcessMain.class, dir, "b", env).awaitReady()) {
                        proof.put("database", database).put("nqPid", a.process.pid())
                                .put("restartPid", b.process.pid()).put("venuePid", venue.process.pid())
                                .put("topology", "A_AND_B_CONCURRENT_NO_RESTART");
                        assertEquals(3, Set.of(a.process.pid(), b.process.pid(), venue.process.pid()).size());
                        proof.put("postgresVersion", value(reader, "SHOW server_version"));
                        proof.put("schemaVersion", value(reader,
                                "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                        assertTrue(proof.path("postgresVersion").asText().startsWith("16."));
                        assertEquals("51", proof.path("schemaVersion").asText());
                        proof.set("before", snapshot(reader));
                        a.send("ARM_B5_STRATEGY");
                        b.send("ARM_B5_STRATEGY");
                        a.send("BEGIN_B5_STRATEGY");
                        b.send("BEGIN_B5_STRATEGY");
                        awaitCut(a);
                        awaitCut(b);
                        String keyA = Files.readAllLines(a.log).stream().filter(line -> line.startsWith("B5_ADMISSION_ATTEMPT key=")).findFirst().orElseThrow();
                        String keyB = Files.readAllLines(b.log).stream().filter(line -> line.startsWith("B5_ADMISSION_ATTEMPT key=")).findFirst().orElseThrow();
                        assertEquals(keyA, keyB);
                        proof.put("attemptedCanonicalKeyA", keyA).put("attemptedCanonicalKeyB", keyB);
                        assertTrue(a.process.isAlive() && b.process.isAlive());
                        assertEquals("0", value(reader, "SELECT count(*) FROM strategy_runs"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                        assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                        proof.set("bothAtCut", snapshot(reader));
                        proof.put("bothAtCutObservedEpochMillis", System.currentTimeMillis());
                        // 屏障释放只安排交叠，不替换任何门禁结果或生产调用，不按数据库结果省略第二次 dispatch。
                        try (var pool = Executors.newFixedThreadPool(2)) {
                            var release = new CyclicBarrier(2);
                            var first = pool.submit(() -> {
                                release.await(5, TimeUnit.SECONDS);
                                return a.send("RELEASE_B5_STRATEGY");
                            });
                            var second = pool.submit(() -> {
                                release.await(5, TimeUnit.SECONDS);
                                return b.send("RELEASE_B5_STRATEGY");
                            });
                            assertEquals("STRATEGY_RELEASED", first.get(10, TimeUnit.SECONDS));
                            assertEquals("STRATEGY_RELEASED", second.get(10, TimeUnit.SECONDS));
                        }
                        String first = a.send("AWAIT_B5_STRATEGY");
                        String second = b.send("AWAIT_B5_STRATEGY");
                        proof.put("aTriggered", first.contains("outcome=TRIGGERED"));
                        proof.put("bTriggered", second.contains("outcome=TRIGGERED"));
                        proof.put("aFailed", first.contains("outcome=FAILED"));
                        proof.put("bFailed", second.contains("outcome=FAILED"));
                        proof.set("final", snapshot(reader));
                        proof.set("venue", facts(endpoint));
                        proof.put("finalObservedEpochMillis", System.currentTimeMillis());
                        long runs = Long.parseLong(value(reader, "SELECT count(*) FROM strategy_runs"));
                        long orders = Long.parseLong(value(reader, "SELECT count(*) FROM orders"));
                        proof.put("logicalDispatchCount", runs).put("orderCount", orders);
                        // 先完整保存失败 oracle，再断言；第二条 run 即使被普通订单幂等挡住也不能算策略去重通过。
                        assertTrue(runs <= 1, "L4_B5_CORRECTNESS_FINDING: duplicate strategy dispatch for one logical window; runs=" + runs);
                        assertTrue(first.contains("outcome=TRIGGERED") ^ second.contains("outcome=TRIGGERED"));
                        assertTrue(first.contains("duplicate_admission") ^ second.contains("duplicate_admission"));
                        assertTrue(!first.contains("outcome=FAILED") && !second.contains("outcome=FAILED"));
                        assertEquals(1, orders);
                        assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                        assertTrue(proof.path("venue").path("placeRequests").asInt() <= 1);
                        assertEquals(0, proof.path("venue").path("cancels").asInt());
                        for (String table : List.of("trades", "ledger_entries")) {
                            assertEquals("0", value(reader, "SELECT count(*) FROM " + table));
                        }
                        assertEquals("0", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
                    }
                }
                assertTrue(pg.databaseAbsent(database));
            }
            proof.put("result", "PASS");
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            // 所有自有资源的 close 已执行；具体退出与容器删除事实保留在控制器日志。
            Path raw = dir.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-AD", repetition.getCurrentRepetition());
        }
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : List.of("strategy_schedules", "strategy_runs", "orders", "ordinary_place_authorities",
                "b5_run_transitions", "trades", "ledger_entries", "execution_intents", "execution_receipts")) {
            result.set(table, mapper.readTree(value(reader,
                    "SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM " + table + " t")));
        }
        result.set("tradeExecuted", mapper.readTree(value(reader,
                "SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM event_store t WHERE event_type='TradeExecuted'")));
        return result;
    }

    private JsonNode facts(String endpoint) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        }
    }

    private static String value(Connection reader, String sql) throws Exception {
        return B5StrategyRunRecoveryProcessTest.value(reader, sql);
    }

    private static void awaitCut(B0Processes.Child child) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains("B5_STRATEGY_CUT localBusyHeld=true")) {
            assertTrue(child.process.isAlive());
            assertTrue(System.nanoTime() < deadline, "strategy pre-dispatch boundary not reached");
            Thread.sleep(20);
        }
    }
}
