package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 资格续跑只使用真实 command/scheduler；数据库写控制仅限封存前 fixture 和连接故障注入。 */
@EnabledIfSystemProperty(named = "nq.b5.resume", matches = "true")
class B5QualificationResumeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Test void currentOrdinaryAndSchedulerRows() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-resume/" + UUID.randomUUID());
        Files.createDirectories(root);
        System.out.println("B5_RESUME_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            int run = 0;
            for (String row : List.of("DUPLICATE1", "DUPLICATE2", "DUPLICATE3", "DEATH_BEFORE", "DEATH_AFTER",
                    "EXECUTED_DEATH", "SCHED_RELEASE", "SCHED_DEATH", "SCHED_CONNECTION_LOSS")) {
                execute(pg, root.resolve(row), row, ++run);
            }
        }
    }

    private void execute(B0Processes.Pg pg, Path dir, String row, int run) throws Exception {
        Files.createDirectories(dir);
        ObjectNode proof = mapper.createObjectNode().put("scenario", row).put("controllerPid", ProcessHandle.current().pid());
        boolean scheduler = row.startsWith("SCHED");
        try {
            try (var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env);
                Class<?> main = scheduler ? B5SchedulerNqProcessMain.class : B0NqProcessMain.class;
                proof.put("database", fixture.name()).put("venuePid", venue.process.pid()).put("externalVenueInput", "oKx");
                try (var reader = fixture.checker(); var a = new B0Processes.Child(main, dir, "nq-a", env).awaitReady();
                     var b = new B0Processes.Child(main, dir, "nq-b", env).awaitReady()) {
                    proof.put("nqPid", a.process.pid()).put("restartPid", b.process.pid());
                    assertNotEquals(a.process.pid(), b.process.pid());
                    proof.put("postgres", value(reader, "SHOW server_version"));
                    assertTrue(proof.path("postgres").asText().startsWith("16."));
                    assertEquals("50", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                    a.send("SET_B5_VENUE b0t4");
                    if (row.startsWith("DUPLICATE")) {
                        a.send("ARM_B5_BEFORE_INSERT"); b.send("ARM_B5_BEFORE_INSERT");
                        a.send("BEGIN_PLACE_B2"); b.send("BEGIN_PLACE_B2");
                        awaitLog(a, "B5_BEFORE_INSERT"); awaitLog(b, "B5_BEFORE_INSERT");
                        assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                        proof.set("beforeInsert", snapshot(reader));
                        try (var pool = Executors.newFixedThreadPool(2)) {
                            var start = new CyclicBarrier(2);
                            var x = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return a.send("RELEASE_B5_INSERT"); });
                            var y = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return b.send("RELEASE_B5_INSERT"); });
                            x.get(10, TimeUnit.SECONDS); y.get(10, TimeUnit.SECONDS);
                        }
                        proof.put("aOutcome", commandOutcome(a)); proof.put("bOutcome", commandOutcome(b));
                        proof.set("afterConcurrent", snapshot(reader));
                        assertEquals(1, facts(endpoint).path("placeRequests").asInt());
                        // 先保留未成交外部事实，再由新进程直接重复命令与恢复，不能由 controller 跳过请求。
                        try (var c = new B0Processes.Child(main, dir, "nq-successor", env).awaitReady()) {
                            proof.put("successorPid", c.process.pid());
                            c.send("PLACE_B2"); c.send("PLACE_B2");
                            proof.set("afterSequential", snapshot(reader));
                            fillAndRecover(reader, c, endpoint);
                        }
                    } else if (row.startsWith("DEATH_")) {
                        a.send(row.equals("DEATH_BEFORE") ? "ARM_B5_PRE_SEND" : "ARM_B5_POST_ARM");
                        a.send("BEGIN_PLACE_B2");
                        awaitLog(a, "B5_CUT " + (row.equals("DEATH_BEFORE") ? "PRE_SEND" : "POST_ARM"));
                        proof.set("atCut", snapshot(reader));
                        b.send("PLACE_B2");
                        assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                        a.kill(); proof.put("oldProcessDeadBeforeRecovery", true);
                        b.send("PLACE_B2"); b.send("B5_RECOVERY");
                        proof.set("afterTakeover", snapshot(reader));
                        b.close();
                        try (var c = new B0Processes.Child(main, dir, "nq-restart", env).awaitReady()) {
                            proof.put("successorPid", c.process.pid());
                            c.send("PLACE_B2"); c.send("B5_RECOVERY"); c.send("RECOVER");
                        }
                        assertEquals(row.equals("DEATH_BEFORE") ? "REVOKED_BEFORE_SEND" : "MAY_HAVE_ESCAPED", state(reader));
                        assertEquals(row.equals("DEATH_BEFORE") ? "CANCELLED" : "SENT", value(reader, "SELECT status FROM orders"));
                        assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                    } else if (row.equals("EXECUTED_DEATH")) {
                        assertTrue(a.send("PLACE_B2").endsWith("ACCEPTED"));
                        control(endpoint, "FILL 10 0.01");
                        a.kill(); proof.put("oldProcessDeadBeforeRecovery", true);
                        b.send("PLACE_B2"); b.send("RECOVER");
                        assertAccounting(reader, endpoint);
                        proof.set("afterTakeover", snapshot(reader));
                        b.close();
                        try (var c = new B0Processes.Child(main, dir, "nq-restart", env).awaitReady()) {
                            proof.put("successorPid", c.process.pid()); c.send("PLACE_B2"); c.send("RECOVER");
                        }
                    } else {
                        proof.put("schedulerWork", "validation-operations/runtime-evidence-refresh")
                                .put("schedulerWindow", "overlapping-invocations-no-durable-window-dedup");
                        a.send("ARM_B5_SCHED"); a.send("BEGIN_B5_SCHED");
                        awaitLog(a, "B5_SCHED_CALLBACK");
                        assertEquals("1", value(reader, "SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND database=(SELECT oid FROM pg_database WHERE datname=current_database())"));
                        proof.set("lockHeld", locks(reader));
                        assertEquals("SCHED_DONE callbacks=0", b.send("RUN_B5_SCHED"));
                        // 真正交易与账务恢复在另一 JVM 执行，证明不依赖 scheduler ownership。
                        assertTrue(b.send("PLACE_B2").endsWith("ACCEPTED"));
                        fillAndRecover(reader, b, endpoint);
                        proof.set("recoveryWhileLocked", snapshot(reader));
                        if (row.equals("SCHED_DEATH")) {
                            a.kill(); proof.put("oldProcessDeadBeforeRecovery", true);
                        } else if (row.equals("SCHED_CONNECTION_LOSS")) {
                            int backend = Integer.parseInt(value(reader, "SELECT pid FROM pg_locks WHERE locktype='advisory' AND database=(SELECT oid FROM pg_database WHERE datname=current_database())"));
                            // 仅切断当前已观察到的自有 fixture lock backend，不写订单或转移 authority。
                            try (var owner = DriverManager.getConnection(fixture.url(), "postgres", "");
                                 var terminate = owner.prepareStatement("SELECT pg_terminate_backend(?)")) {
                                terminate.setInt(1, backend); try (var result = terminate.executeQuery()) { assertTrue(result.next() && result.getBoolean(1)); }
                            }
                            proof.put("terminatedLockBackend", backend);
                        } else {
                            a.send("RELEASE_B5_SCHED");
                            assertEquals("SCHED_DONE callbacks=1", a.send("AWAIT_B5_SCHED"));
                        }
                        awaitNoLock(reader);
                        assertEquals("SCHED_DONE callbacks=1", b.send("RUN_B5_SCHED"));
                        if (row.equals("SCHED_CONNECTION_LOSS")) {
                            a.send("RELEASE_B5_SCHED"); a.send("AWAIT_B5_SCHED");
                            assertTrue(Files.readString(a.log).contains("result=FAILED"));
                            // stale scheduler 本身无交易 callback；再直接触达 ordinary boundary 仍只有原订单。
                            a.send("PLACE_B2"); a.send("RECOVER");
                        }
                        assertEquals("SCHED_DONE callbacks=2", b.send("RUN_B5_SCHED"));
                        proof.put("bAggregateExecutions", 2).put("overlapSkipped", true);
                        proof.set("lockReleased", locks(reader));
                        try (var c = new B0Processes.Child(main, dir, "nq-restart", env).awaitReady()) {
                            proof.put("successorPid", c.process.pid());
                            assertEquals("SCHED_DONE callbacks=1", c.send("RUN_B5_SCHED"));
                            c.send("PLACE_B2"); c.send("RECOVER");
                        }
                    }
                    proof.set("final", snapshot(reader)); proof.set("venue", facts(endpoint));
                    assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                    assertEquals("OKX", value(reader, "SELECT venue FROM orders"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM execution_intents"));
                    assertEquals("0", value(reader, "SELECT count(*) FROM execution_receipts"));
                    if (!row.startsWith("DEATH_")) assertAccounting(reader, endpoint);
                    else {
                        assertEquals("0", value(reader, "SELECT count(*) FROM trades"));
                        assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries"));
                    }
                    assertTrue(facts(endpoint).path("placeRequests").asInt() <= 1);
                    assertEquals(0, facts(endpoint).path("cancels").asInt());
                    awaitNoLock(reader);
                }
            }
            proof.put("result", "PASS").put("cleanup", true);
            System.out.println("B5_RESUME_PASS " + row);
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            Path raw = dir.resolve("raw-proof.json");
            Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-R", run);
        }
    }

    private String commandOutcome(B0Processes.Child child) throws Exception {
        try { return child.send("AWAIT").replaceAll("ord-[a-f0-9-]+", "ORDER"); }
        catch (AssertionError failure) {
            String log = Files.readString(child.log);
            // 同时 INSERT 的输家可能得到唯一键导致的事务失败；保留该可用性事实，不能掩盖其它异常。
            assertTrue(log.contains("DuplicateKeyException") || log.contains("uq_orders_account_client_order"), log);
            assertFalse(child.process.isAlive());
            return "DUPLICATE_INSERT_TRANSACTION_FAILURE";
        }
    }

    private void fillAndRecover(Connection reader, B0Processes.Child child, String endpoint) throws Exception {
        control(endpoint, "FILL 10 0.01"); child.send("RECOVER"); child.send("PLACE_B2"); child.send("RECOVER");
        assertAccounting(reader, endpoint);
    }
    private void assertAccounting(Connection reader, String endpoint) throws Exception {
        new B2RealProcessProofTest().assertAccounting(reader, facts(endpoint));
        assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
        assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
        assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
        assertEquals("4", value(reader, "SELECT count(*) FROM ledger_entries"));
        assertEquals("MAY_HAVE_ESCAPED", state(reader));
        assertEquals(1, facts(endpoint).path("placeRequests").asInt());
    }
    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        for (String table : List.of("orders", "ordinary_place_authorities", "trades", "event_store", "ledger_entries", "audit_logs", "execution_intents", "execution_receipts"))
            result.set(table, mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        return result;
    }
    private JsonNode locks(Connection reader) throws Exception {
        return mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM (SELECT pid,locktype,mode,granted,classid,objid FROM pg_locks WHERE locktype='advisory' AND database=(SELECT oid FROM pg_database WHERE datname=current_database())) t"));
    }
    private void awaitNoLock(Connection reader) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!locks(reader).isEmpty()) { assertTrue(System.nanoTime() < until); Thread.sleep(30); }
    }
    private static void awaitLog(B0Processes.Child child, String marker) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.readString(child.log).contains(marker)) {
            assertTrue(child.process.isAlive(), child.log.toString()); assertTrue(System.nanoTime() < until, marker); Thread.sleep(20);
        }
    }
    private static String value(Connection reader, String sql) throws Exception {
        try (var s = reader.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
    private String state(Connection reader) throws Exception { return value(reader, "SELECT state FROM ordinary_place_authorities"); }
    private JsonNode facts(String endpoint) throws Exception {
        return mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    private void control(String endpoint, String command) throws Exception {
        assertEquals(200, http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
