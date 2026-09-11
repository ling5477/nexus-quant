package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.net.URI;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 有限命令波次产生全部业务事实；独立只读连接在运行中采样，不注入故障。 */
@EnabledIfSystemProperty(named = "nq.l5", matches = "true")
class L5BoundedWorkloadTest {
    private B0Processes.Child wire;
    private final boolean killRun = Boolean.getBoolean("nq.l5.kill");
    private final boolean concurrentRun = killRun || System.getProperty("nq.l5.level", "ALL").startsWith("C");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BACKLOG = "SELECT count(*) FROM orders o WHERE o.status NOT IN ('FILLED','CANCELLED','RISK_REJECTED') "
            + "OR (o.status='FILLED' AND (NOT EXISTS(SELECT 1 FROM trades t WHERE t.order_id=o.order_id) "
            + "OR EXISTS(SELECT 1 FROM trades t WHERE t.order_id=o.order_id AND NOT EXISTS(SELECT 1 FROM ledger_entries l WHERE l.ref_id=t.trade_id))))";
    private static final String UNKNOWN = "SELECT count(*) FROM orders o JOIN ordinary_place_authorities a USING(order_id) "
            + "WHERE a.state='MAY_HAVE_ESCAPED' AND o.external_order_id IS NULL AND o.status NOT IN ('FILLED','CANCELLED','RISK_REJECTED')";

    @Test void baselineStopsAtFirstFailure() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/l5-bounded/" + UUID.randomUUID());
        assertTrue(List.of("ALL", "S1", "S2", "S3", "C1", "C2", "C3").contains(System.getProperty("nq.l5.level", "ALL")), "unknown scale selection");
        System.out.println("L5_ROOT " + root);
        for (int level = 1; level <= 3; level++) {
            if (!System.getProperty("nq.l5.level", "ALL").equals("ALL")
                    && !System.getProperty("nq.l5.level").equals((concurrentRun ? "C" : "S") + level)) continue;
            execute(root.resolve((concurrentRun ? "C" : "S") + level), level);
        }
    }

    void execute(Path dir, int level) throws Exception {
        Files.createDirectories(dir);
        int concurrency = concurrentRun ? (level == 1 ? 2 : 4) : (level == 3 ? 4 : level);
        int count = concurrentRun ? (level == 1 ? 120 : level == 2 ? 240 : 228) : (level == 3 ? 240 : 120);
        ObjectNode proof = JSON.createObjectNode().put("level", killRun ? "K1" : (concurrentRun ? "C" : "S") + level).put("concurrency", concurrency)
                .put("expectedOrders", count).put("expectedStrategyRuns", killRun ? 1 : level == 3 ? 12 : 0)
                .put("controllerPid", ProcessHandle.current().pid());
        List<B0Processes.Child> children = new CopyOnWriteArrayList<>();
        var progress = JSON.createArrayNode();
        AtomicReference<Throwable> samplingFailure = new AtomicReference<>();
        long started = System.nanoTime();
        String ownedContainer = null;
        long ownedVenuePid = 0;
        int venuePort = 0;
        int databasePort = 0;
        long initialDisk = Files.getFileStore(dir).getUsableSpace();
        assertTrue(initialDisk > 2L * 1024 * 1024 * 1024, "insufficient disk budget");
        try {
            try (var pg = B0Processes.Pg.startBounded()) {
                ownedContainer = pg.ownedContainerId();
                databasePort = URI.create(pg.ownedUrl().substring(5)).getPort();
                String database;
                try (var fixture = B0Fixture.create(pg);
                     var venue = new B0Processes.Child(L5VenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                    ownedVenuePid = venue.process.pid();
                    database = fixture.name();
                    venuePort = Integer.parseInt(venue.ready());
                    String endpoint = "http://127.0.0.1:" + venuePort;
                    var env = B0Processes.cleanEnvironment();
                    env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                    fixture.initialize(true, endpoint, env, killRun);
                    if (level == 3) seedStrategies(fixture, concurrentRun);
                    if (killRun) seedKillStrategy(fixture);
                    proof.put("database", database).put("venuePid", venue.process.pid());
                    http(endpoint, "L5_OPEN");
                    try {
                        if (System.getProperty("nq.l5.fault", "NONE").equals("F3")) {
                            var proxyEnv = B0Processes.cleanEnvironment(); proxyEnv.put("NQ_B4_DB", fixture.url());
                            wire = new B0Processes.Child(B4PgWireProxyMain.class, dir, "pg-wire", proxyEnv).awaitReady();
                        }
                        for (int jvm = 0; jvm < concurrency; jvm++) {
                            var processEnv = new LinkedHashMap<>(env);
                            if (jvm == 0 && wire != null) processEnv.put("NQ_B0_DB", fixture.url().replaceFirst(
                                    "127\\.0\\.0\\.1:[0-9]+", "127.0.0.1:" + wire.ready()));
                            children.add(new B0Processes.Child(killRun ? L5KillNqProcessMain.class : System.getProperty("nq.l5.fault", "NONE").equals("NONE")
                                    ? L5NqProcessMain.class : L5FaultNqProcessMain.class, dir, "nq-" + jvm, processEnv).awaitReady());
                        }
                        try (var reader = fixture.checker(); var samplingReader = fixture.checker();
                             var sampler = Executors.newSingleThreadScheduledExecutor()) {
                            if (Boolean.getBoolean("nq.l5.deterministic")) reader.setNetworkTimeout(Runnable::run, 5000);
                            assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                            assertEquals("f", value(reader, "SELECT has_table_privilege(current_user,'orders','UPDATE')"));
                            // 一次采样使用一致的只读 MVCC 快照，不锁业务行；HTTP 采样前立即提交。
                            samplingReader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                            samplingReader.setAutoCommit(false);
                            sampler.scheduleWithFixedDelay(() -> {
                                try {
                                    synchronized (progress) {
                                        if (progress.size() >= 7200) throw new IllegalStateException("progress budget exceeded");
                                        ObjectNode current = sample(samplingReader);
                                        if (killRun) current.set("kill", killState(samplingReader));
                                        current.put("ownedNq", children.stream().filter(c -> c.process.isAlive()).count());
                                        samplingReader.commit();
                                        current.set("venueExecutor", http(endpoint, "METRICS"));
                                        progress.add(current);
                                    }
                                } catch (Throwable error) { samplingFailure.compareAndSet(null, error); }
                            }, 0, 250, TimeUnit.MILLISECONDS);
                            try {
                                if (killRun) {
                                    killWorkload(reader, children, endpoint, proof);
                                } else if (concurrentRun) {
                                    concurrentWorkload(reader, children, endpoint, env, dir, level, count, proof);
                                } else {
                                // 每波仅向每个 JVM 发送一条命令；不建立可无限积压的 Future 队列。
                                for (int base = 1; base <= count; base += concurrency) {
                                    if (samplingFailure.get() != null) throw new IllegalStateException("sampling failed", samplingFailure.get());
                                    assertTrue(Files.getFileStore(dir).getUsableSpace() > Math.max(2L * 1024 * 1024 * 1024, initialDisk / 5), "disk safety threshold");
                                    // 默认每 JVM 同账户限频为 5/s；驱动最多 4/s，不改真实风控预算。
                                    Thread.sleep(250);
                                    for (int j = 0; j < concurrency; j++) children.get(j).startCommand("L5_PLACE " + (base + j));
                                    for (var child : children) assertEquals("L5_PLACE ACCEPTED", child.result());
                                    assertTrue(Duration.ofNanos(System.nanoTime() - started).toMinutes() < 30, "run watchdog");
                                }
                                proof.put("initialBacklog", number(reader, BACKLOG));
                                assertEquals(count, number(reader, BACKLOG));
                                http(endpoint, "FILL");
                                drain(reader, children, count, proof);
                                // 顺序重复已知命令只核对稳定identity；跨JVM重复竞争留给后续批次。
                                for (int j = 0; j < concurrency; j++) children.get(j).send("L5_PLACE " + (j + 1));
                                if (level == 3) {
                                    // 三个已到期年度window；真实scanner计算dueAt，不改Clock和业务状态。
                                    for (int window = 0; window < 3; window++) {
                                        Thread.sleep(1100);
                                        children.get(0).send("L5_SCAN");
                                        http(endpoint, "FILL");
                                        drain(reader, children, count + (window + 1) * 4, proof);
                                        children.get(0).send("L5_PROJECT");
                                    }
                                    children.get(0).send("L5_SCAN");
                                }
                                }
                                proof.set("facts", facts(reader));
                                proof.set("venue", http(endpoint, null));
                                proof.put("finalBacklog", number(reader, BACKLOG));
                                proof.put("correctnessRequiredUnresolved", number(reader, UNKNOWN));
                                proof.put("pgAppActive", number(reader, "SELECT count(*) FROM pg_stat_activity WHERE usename='nq_b0_app' AND state<>'idle'"));
                                proof.set("observations", JSON.readTree(children.get(0).send("L5_OBSERVE")));
                                children.get(0).send("L5_RECONCILE"); children.get(0).send("L5_PROJECT");
                                assertEquals(proof.get("facts"), facts(reader), "replay must preserve durable business facts");
                                if (killRun) {
                                    var kill = proof.withObject("/concurrent/kill");
                                    kill.set("finalState", killState(reader));
                                    kill.set("events", JSON.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM kill_switch_events t")));
                                    kill.set("riskEvents", JSON.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM risk_events t")));
                                }
                                if (concurrentRun) ((ObjectNode) proof.get("concurrent")).set("cursorFinal", cursor(reader));
                                Thread.sleep(1000);
                            } catch (Exception | AssertionError failure) {
                                proof.set("failureFacts", facts(reader));
                                proof.set("venue", http(endpoint, null));
                                proof.put("riskReasons", value(reader, "SELECT coalesce(string_agg(reason,';'),'') FROM risk_events"));
                                throw failure;
                            } finally {
                                // 正常收尾等待在途只读采样，避免自己中断HTTP后误报采样失败。
                                sampler.shutdown();
                                if (!sampler.awaitTermination(15, TimeUnit.SECONDS)) {
                                    sampler.shutdownNow();
                                    throw new IllegalStateException("sampler did not stop within cleanup budget");
                                }
                            }
                            if (samplingFailure.get() != null) throw new IllegalStateException("progress sampling failed", samplingFailure.get());
                            proof.set("progress", progress);
                        }
                    } finally {
                        try { B0Processes.closeChildren(children); }
                        finally {
                            if (wire != null) {
                                int port = Integer.parseInt(wire.ready());
                                wire.close();
                                proof.withObject("/concurrent").put("proxyRemaining", wire.process.isAlive() ? 1 : 0);
                                try (var socket = new Socket()) {
                                    socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
                                    throw new AssertionError("owned proxy port still listening");
                                } catch (IOException expected) {
                                    proof.withObject("/concurrent").put("proxyPortReleased", true);
                                }
                                wire = null;
                            }
                        }
                    }
                }
                assertTrue(pg.databaseAbsent(database));
            }
            proof.put("cleanup", true).put("ownedNqRemaining", children.stream().filter(c -> c.process.isAlive()).count());
            boolean portOpen;
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", venuePort), 500); portOpen = true;
            } catch (Exception expected) { portOpen = false; }
            assertTrue(!portOpen, "venue port still listening");
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", databasePort), 500);
                throw new AssertionError("owned PostgreSQL port still listening");
            } catch (IOException expected) {
                proof.put("databasePortReleased", true);
            }
            proof.put("venuePortReleased", true).put("elapsedSeconds", (System.nanoTime() - started) / 1e9);
            var resources = proof.putArray("resources");
            try (var paths = Files.list(dir)) {
                for (var file : paths.filter(p -> p.getFileName().toString().startsWith("l5-metrics-")).toList()) {
                    for (String line : Files.readAllLines(file)) resources.add(JSON.readTree(line));
                }
            }
            proof.put("result", "MEASURED");
            long rawBytes = 0;
            try (var files = Files.list(dir)) {
                for (var file : files.filter(Files::isRegularFile).toList()) rawBytes += Files.size(file);
            }
            assertTrue(rawBytes < 1024L * 1024 * 1024, "raw artifact budget");
            proof.put("rawBytesBeforeProof", rawBytes);
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAIL").put("failure", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            if (Boolean.getBoolean("nq.l5.deterministic")) {
                var cleanup = proof.withObject("/concurrent").putObject("targetCleanup");
                cleanup.put("ownedNq", children.stream().filter(c -> c.process.isAlive()).count());
                cleanup.put("ownedVenue", ProcessHandle.of(ownedVenuePid).filter(ProcessHandle::isAlive).isPresent() ? 1 : 0);
                cleanup.put("ownedPg", ownedContainer == null || B0Processes.command("docker", "ps", "-a", "--filter", "id=" + ownedContainer, "--format", "{{.ID}}").isBlank() ? 0 : 1);
                assertEquals(0, cleanup.path("ownedNq").asInt());
                assertEquals(0, cleanup.path("ownedVenue").asInt());
                assertEquals(0, cleanup.path("ownedPg").asInt());
            }
            if (concurrentRun) {
                var actors = ((ObjectNode) proof.withObject("/concurrent")).putArray("actors");
                try (var files = Files.list(dir)) {
                    for (var file : files.filter(f -> f.getFileName().toString().startsWith("l5c-actor-")).toList()) actors.add(JSON.readTree(Files.readString(file)));
                }
            }
            proof.set("progress", progress);
            Files.writeString(dir.resolve("raw-proof.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
        SyntheticEvidenceExport.execute("l5_measurement.py", dir.resolve("raw-proof.json").toString());
        System.out.println("L5_PASS " + (killRun ? "K1" : (concurrentRun ? "C" : "S") + level));
    }

    /** 已确认 ACCEPTED 的命令全部早于 ENGAGE；切换后新身份单独验证，避免按 Venue 时间误判。 */
    private void killWorkload(Connection reader, List<B0Processes.Child> children, String endpoint, ObjectNode proof) throws Exception {
        ObjectNode concurrent = proof.putObject("concurrent");
        ObjectNode kill = concurrent.putObject("kill");
        kill.set("initialState", killState(reader));
        assertEquals("DISENGAGED", kill.path("initialState").path("status").asText());
        concurrent.set("cursorStart", cursor(reader));
        for (int base = 1; base <= 60; base += 2) produce(children, base, 2);
        proof.put("initialBacklog", number(reader, BACKLOG));
        assertEquals(60, number(reader, BACKLOG));
        concurrent.put("producerStartMillis", System.currentTimeMillis());
        for (var child : children) assertEquals("L5C_STARTED", child.send("L5C_BEGIN"));
        for (int base = 61; base <= 120; base += 2) {
            produce(children, base, 2);
            http(endpoint, "FILL_NEW");
        }
        // 只启动一个真实策略窗口；其未成交 Order 在 ENGAGE 后继续完成 bookkeeping。
        Thread.sleep(1100);
        children.get(0).send("L5_SCAN");
        kill.put("lastAcceptedResponseMillis", System.currentTimeMillis());
        concurrent.put("producerStopMillis", System.currentTimeMillis());
        assertEquals(121, number(reader, "SELECT count(*) FROM orders"));
        assertTrue(number(reader, "SELECT count(*) FROM trades") > 0);
        assertTrue(number(reader, BACKLOG) > 0);
        kill.set("beforeFacts", facts(reader));
        kill.set("beforeVenue", http(endpoint, null));
        kill.set("beforeSample", sample(reader));
        kill.set("beforeCursor", cursor(reader));
        kill.put("requestMillis", System.currentTimeMillis());
        String result = children.get(0).send("L5_KILL_ENGAGE");
        kill.put("responseMillis", System.currentTimeMillis()).put("controlResult", result);
        kill.set("durableState", killState(reader));
        kill.put("durableReadMillis", System.currentTimeMillis());
        assertEquals("ENGAGED", kill.path("durableState").path("status").asText());
        assertEquals("L5_KILL ENGAGED " + kill.path("durableState").path("version").asLong(), result);
        var rejected = kill.putArray("newCommands");
        for (int identity = 121; identity <= 124; identity++) {
            Thread.sleep(300);
            var command = rejected.addObject().put("identity", identity).put("submittedMillis", System.currentTimeMillis());
            var child = children.get((identity - 121) % children.size());
            command.put("pid", child.process.pid()).put("result", child.send("L5_PLACE " + identity))
                    .put("completedMillis", System.currentTimeMillis());
            assertEquals("L5_PLACE RISK_REJECTED", command.path("result").asText());
            assertEquals("ENGAGED", killState(reader).path("status").asText());
        }
        // Venue 正常成交此前已接受订单，不制造 CANCEL 或恢复发送；actor 持续运行。
        kill.put("releaseExistingFillsMillis", System.currentTimeMillis());
        http(endpoint, "FILL");
        long started = System.nanoTime();
        int rounds = 0;
        while (number(reader, BACKLOG) > 0 && rounds++ < 40) {
            Thread.sleep(500);
            for (var child : children) child.send("L5_OBSERVE");
            children.get(0).send("L5_PROJECT");
            assertEquals("ENGAGED", killState(reader).path("status").asText());
        }
        assertEquals(0, number(reader, BACKLOG), "existing actionable work must converge under Kill");
        for (var child : children) assertEquals("L5C_STOPPED", child.send("L5C_STOP"));
        children.get(0).send("L5_PROJECT");
        proof.put("drainSeconds", (System.nanoTime() - started) / 1e9).put("scanIterations", rounds);
        kill.set("afterSample", sample(reader));
    }

    private static JsonNode killState(Connection reader) throws Exception {
        return JSON.readTree(value(reader, "SELECT to_jsonb(k)::text FROM kill_switch_states k WHERE scope='GLOBAL_TRADING'"));
    }

    /** 仅在 NQ 启动前建立策略定义，实际 run/Order/work 全由 scanner 创建。 */
    private static void seedKillStrategy(B0Fixture fixture) throws Exception {
        try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
            s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot) "
                    + "SELECT 'l5-kill-strategy','l5-kill-strategy','L5 Kill fixture','TEST','OKX',account_id,'SIM',true,"
                    + "'{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"0.1\"}'::jsonb FROM accounts WHERE account_code='b0-account'");
            s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,enabled,dedup_scope,exchange_code,account_id,trade_env,created_at) "
                    + "SELECT 'l5-kill-schedule','l5-kill-strategy','0 0 0 1 1 *','UTC',true,'SCHEDULE_WINDOW','OKX',account_id,'SIM',date_trunc('year',CURRENT_TIMESTAMP)-INTERVAL '1 year' FROM accounts WHERE account_code='b0-account'");
        }
    }


    /** 老订单保持正常未成交，新订单陆续成交；仅控制 Venue 的正常成交时机。 */
    private void concurrentWorkload(Connection reader, List<B0Processes.Child> children, String endpoint,
            Map<String, String> env, Path dir, int level, int count, ObjectNode proof) throws Exception {
        ObjectNode concurrent = proof.putObject("concurrent");
        concurrent.set("cursorStart", cursor(reader));
        concurrent.put("limit", 40).put("persistentPrefix", 60).put("producerWaveDelayMillis", 300);
        int width = children.size();
        if ("F3".equals(System.getProperty("nq.l5.fault"))) {
            String db = value(reader, "SELECT current_database()");
            concurrent.putObject("dbFaultPlan").put("qualificationRunId", db)
                    .put("client_order_id", "l5" + db.substring(db.length() - 20) + "0085")
                    .put("ownerPid", children.get(0).process.pid()).put("target", "ACK")
                    .put("armedBeforeWorkloadMillis", System.currentTimeMillis());
        }
        if (Boolean.getBoolean("nq.l5.deterministic")) {
            assertEquals("F2", System.getProperty("nq.l5.fault"));
            String db = value(reader, "SELECT current_database()");
            ObjectNode plan = concurrent.putObject("targetPlan").put("qualificationRunId", db)
                    .put("client_order_id", "l5" + db.substring(db.length() - 20) + "0085")
                    .put("exchange_trade_id", "b0-fill-b0-venue-85").put("trace_id", "l5-trace-85")
                    .put("expectedTradeKey", "OKX / b0-fill-b0-venue-85").put("ownerPid", children.get(0).process.pid())
                    .put("boundary", "TRADE_AND_REQUIRED_EVENT_COMMITTED_BEFORE_LEDGER");
            ObjectNode beforeArm = facts(reader);
            assertEquals("ARMED", children.get(0).send("L5_TARGET_ARM"));
            plan.set("beforeArm", beforeArm); plan.set("afterArm", facts(reader));
            assertEquals(beforeArm, plan.get("afterArm"), "arming must not mutate business facts");
            plan.put("armedMillis", System.currentTimeMillis()).put("armingBusinessMutation", 0);
        }
        for (int base = 1; base <= 60; base += width) produce(children, base, width);
        proof.put("initialBacklog", number(reader, BACKLOG));
        assertEquals(60, number(reader, BACKLOG));
        concurrent.put("consumerStartMillis", System.currentTimeMillis());
        for (var child : children) assertEquals("L5C_STARTED", child.send("L5C_BEGIN"));
        concurrent.put("producerStartMillis", System.currentTimeMillis());
        String faultFamily = System.getProperty("nq.l5.fault", "NONE");
        assertTrue(List.of("NONE", "F1", "F2", "F3").contains(faultFamily));
        for (int base = 61; base <= count; base += width) {
            if (Boolean.getBoolean("nq.l5.deterministic") && base == 85) {
                deterministicFault(reader, children, endpoint, env, dir, concurrent);
            } else if ((faultFamily.equals("F2") || faultFamily.equals("F3")) && base == 85) {
                base = processFault(reader, children, endpoint, env, dir, concurrent, base, width);
            } else if (faultFamily.equals("F1") && base == 85) {
                ObjectNode fault = concurrent.putObject("fault").put("family", faultFamily)
                        .put("startMillis", System.currentTimeMillis()).put("base", base);
                String mode = Integer.getInteger("nq.l5.repetition", 1) == 2 ? "B1_LOST_ACK" : "B1_ACCEPTED_TIMEOUT";
                fault.put("mode", mode);
                fault.set("before", sample(reader));
                http(endpoint, mode);
                for (int j = 0; j < width; j++) children.get(j).startCommand("L5_PLACE " + (base + j));
                // 不确定期间继续采集业务事实，其余对账actor仍在真实运行。
                Thread.sleep(500);
                fault.set("during", sample(reader));
                fault.set("venueDuring", http(endpoint, null));
                for (var child : children) assertEquals("L5_PLACE ACCEPTED", child.result());
                if (mode.equals("B1_LOST_ACK")) http(endpoint, "RELEASE_LOST");
                http(endpoint, "L5_OPEN");
                fault.put("endMillis", System.currentTimeMillis());
            } else produce(children, base, width);
            http(endpoint, "FILL_NEW");
            if (level == 3 && (base + width - 61) % 56 == 0) {
                Thread.sleep(1100);
                children.get(0).send("L5_SCAN");
                http(endpoint, "FILL_NEW");
                children.get(0).send("L5_PROJECT");
                // 每次scanner至多四个PLACE；故障资格保留下一普通波次的真实限频余量。
                if (!faultFamily.equals("NONE")) Thread.sleep(1100);
            }
        }
        concurrent.put("producerStopMillis", System.currentTimeMillis());
        concurrent.set("fairnessFacts", facts(reader));
        concurrent.set("fairnessVenue", http(endpoint, null));
        http(endpoint, "FILL");
        long started = System.nanoTime();
        int rounds = 0;
        while (number(reader, BACKLOG) > 0 && rounds++ < 60) {
            Thread.sleep(500);
            // 每次请求也检查后台 actor 的异常，不因进程仍存活而忽略失败。
            for (var child : children) child.send("L5_OBSERVE");
            if (level == 3) children.get(0).send("L5_PROJECT");
        }
        assertEquals(0, number(reader, BACKLOG), "L5 concurrent actionable backlog did not converge");
        for (var child : children) assertEquals("L5C_STOPPED", child.send("L5C_STOP"));
        if (level == 3) children.get(0).send("L5_PROJECT");
        proof.put("drainSeconds", (System.nanoTime() - started) / 1e9).put("scanIterations", rounds);
        concurrent.set("cursorBeforeRestart", cursor(reader));
        // 正常停止一个已完成 actor，再启动新 JVM读取同一数据库；没有运行中杀进程。
        var old = children.get(width - 1);
        concurrent.put("oldPid", old.process.pid());
        old.startCommand("STOP");
        assertTrue(old.process.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, old.process.exitValue()); old.close();
        concurrent.put("oldExitMillis", System.currentTimeMillis());
        var replacement = new B0Processes.Child(L5NqProcessMain.class, dir, "nq-restarted", env).awaitReady();
        children.set(width - 1, replacement);
        concurrent.put("newPid", replacement.process.pid()).put("newStartMillis", System.currentTimeMillis());
        concurrent.set("cursorAfterRestart", cursor(reader));
        assertEquals(concurrent.get("cursorBeforeRestart"), concurrent.get("cursorAfterRestart"));
        replacement.send("L5C_STEP");
        concurrent.set("cursorAfterStep", cursor(reader));
        if (level == 3) children.get(0).send("L5_SCAN");
    }

    /** 仅在短故障窗口暂停qualification投喂；所有业务事务和恢复仍由生产调用完成。 */
    private void deterministicFault(Connection reader, List<B0Processes.Child> children, String endpoint,
            Map<String, String> env, Path dir, ObjectNode concurrent) throws Exception {
        var victim = children.get(0);
        ObjectNode plan = (ObjectNode) concurrent.get("targetPlan");
        ObjectNode fault = concurrent.putObject("fault").put("family", "F2").put("mode", "AFTER_TRADE_COMMIT")
                .put("oldPid", victim.process.pid()).put("startMillis", System.currentTimeMillis());
        var handshake = fault.putArray("handshake");
        handshake.addObject().put("state", "ARMED").put("timeMillis", plan.path("armedMillis").asLong());
        fault.set("before", sample(reader));
        // 前84个已完成PLACE，目标先于本波其余三单进入既有Venue，身份无需事后猜测。
        assertEquals("L5_PLACE ACCEPTED", victim.send("L5_PLACE 85"));
        for (int j = 1; j < children.size(); j++) children.get(j).startCommand("L5_PLACE " + (85 + j));
        for (int j = 1; j < children.size(); j++) assertEquals("L5_PLACE ACCEPTED", children.get(j).result());
        long gateDeadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        fault.put("gateStartMillis", System.currentTimeMillis());
        for (var child : children) assertEquals("PAUSED", child.send("L5C_PAUSE"));
        ObjectNode quiescent = facts(reader);
        for (var child : children) assertEquals("PAUSED", child.send("L5C_PAUSE"));
        fault.set("gateBefore", quiescent); fault.set("gateAfter", facts(reader));
        assertEquals(quiescent, fault.get("gateAfter"), "orchestration gate must not mutate business facts");
        String outcome = System.getProperty("nq.l5.targetOutcome", "KILL");
        assertTrue(List.of("KILL", "RELEASE", "ASSERTION", "EXCEPTION", "TIMEOUT").contains(outcome));
        fault.put("outcome", outcome).put("faultKills", 0);
        if (!outcome.equals("TIMEOUT")) http(endpoint, "FILL_NEW");
        boolean reached = false;
        for (int attempt = 0; attempt < 6 && !reached; attempt++) {
            assertEquals("STEP_REQUESTED", victim.send("L5C_TARGET_STEP"));
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            boolean complete = false;
            while (System.nanoTime() < deadline) {
                if (Files.readString(victim.log).contains("L5_TARGET_MATCHED ")) { reached = true; break; }
                if (victim.send("L5C_TARGET_STATUS").equals("COMPLETE")) { complete = true; break; }
                Thread.sleep(25);
            }
            if (!reached && !complete) throw new AssertionError("BLOCKED / FAULT_TARGET_NOT_REACHED");
        }
        assertTrue(reached, "BLOCKED / FAULT_TARGET_NOT_REACHED");
        handshake.addObject().put("state", "TARGET_MATCHED").put("timeMillis", System.currentTimeMillis());
        ObjectNode cut = facts(reader);
        JsonNode trade = null;
        for (JsonNode candidate : cut.path("trades")) if (candidate.path("exchange_trade_id").asText().equals(plan.path("exchange_trade_id").asText())) trade = candidate;
        assertTrue(trade != null, "independent reader must see durable target Trade");
        String tradeId = trade.path("trade_id").asText();
        String orderId = trade.path("order_id").asText();
        assertTrue(cut.path("orders").findValues("order_id").stream().anyMatch(n -> n.asText().equals(orderId)));
        assertEquals(1, number(reader, "SELECT count(*) FROM orders WHERE order_id='" + orderId + "' AND client_order_id='" + plan.path("client_order_id").asText() + "'"));
        assertEquals(1, number(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted' AND payload_json->'payload'->>'trade_id'='" + tradeId + "'"));
        assertEquals(0, number(reader, "SELECT count(*) FROM ledger_entries WHERE ref_id='" + tradeId + "'"));
        handshake.addObject().put("state", "DURABLE_BOUNDARY_CONFIRMED").put("timeMillis", System.currentTimeMillis());
        fault.put("targetTradeId", tradeId).put("targetOrderId", orderId).put("targetFillId", plan.path("exchange_trade_id").asText());
        fault.set("atCut", cut); fault.set("during", sample(reader));
        fault.set("barrierAfter", facts(reader));
        assertEquals(cut, fault.get("barrierAfter"), "paused barrier must not add business mutations");
        fault.put("barrierBusinessMutation", 0);
        handshake.addObject().put("state", "FAULT_READY").put("timeMillis", System.currentTimeMillis());
        assertTrue(System.nanoTime() < gateDeadline, "BLOCKED / FAULT_TARGET_NOT_REACHED: expired fault window");
        if (!outcome.equals("KILL")) {
            assertEquals("RELEASED", victim.send("L5_TARGET_RELEASE"));
            for (var child : children) assertEquals("RESUMED", child.send("L5C_RESUME"));
            fault.put("released", true);
            if (outcome.equals("ASSERTION")) throw new AssertionError("EXPECTED_TARGET_ASSERTION");
            throw new IllegalStateException("EXPECTED_TARGET_" + outcome);
        }
        assertEquals(plan.path("ownerPid").asLong(), victim.process.pid());
        victim.kill(); victim.close();
        fault.put("faultKills", 1);
        fault.put("deathMillis", System.currentTimeMillis()).put("oldDead", !victim.process.isAlive());
        handshake.addObject().put("state", "FAULT_INJECTED").put("timeMillis", System.currentTimeMillis());
        for (int j = 1; j < children.size(); j++) assertEquals("RESUMED", children.get(j).send("L5C_RESUME"));
        fault.put("gateEndMillis", System.currentTimeMillis());
        handshake.addObject().put("state", "RECOVERY_RELEASED").put("timeMillis", System.currentTimeMillis());
        var replacement = new B0Processes.Child(L5FaultNqProcessMain.class, dir, "nq-exact-recovery", env);
        children.set(0, replacement); replacement.awaitReady();
        fault.put("newPid", replacement.process.pid()).put("newStartMillis", System.currentTimeMillis());
        replacement.send("L5C_BEGIN"); replacement.send("L5_PROJECT");
        fault.put("endMillis", System.currentTimeMillis());
    }

    /** 复用既有B1/B4/V51屏障，仅终止当前fixture持有的Child对象。 */
    private int processFault(Connection reader, List<B0Processes.Child> children, String endpoint,
            Map<String, String> env, Path dir, ObjectNode concurrent, int base, int width) throws Exception {
        int repetition = Integer.getInteger("nq.l5.repetition", 1);
        assertTrue(repetition >= 1 && repetition <= 3);
        var victim = children.get(0);
        boolean databaseFault = wire != null;
        ObjectNode fault = concurrent.putObject("fault").put("family", databaseFault ? "F3" : "F2")
                .put("startMillis", System.currentTimeMillis()).put("oldPid", victim.process.pid());
        fault.set("before", sample(reader));
        if (databaseFault) {
            String target = repetition == 3 ? "LEDGER" : "ACK";
            String mode = repetition == 1 ? "BEFORE_PAUSE" : "AFTER_PAUSE";
            fault.put("mode", mode).put("target", target).put("proxyPid", wire.process.pid());
            if (target.equals("LEDGER")) produce(children, base, width);
            victim.send("ARM_B4_TX " + target + " WIRE");
            wire.send("ARM " + mode);
            if (target.equals("ACK")) {
                for (int j = 0; j < width; j++) children.get(j).startCommand("L5_PLACE " + (base + j));
            } else http(endpoint, "FILL_NEW");
            String marker = "B4_WIRE_CUT " + mode + " " + (repetition == 1
                    ? "COMMIT_NOT_FORWARDED" : "SERVER_COMMIT_CONFIRMED_RESPONSE_WITHHELD");
            if (target.equals("LEDGER")) {
                while (!Files.readString(wire.log).contains(marker) && base < 109) {
                    base += width;
                    if (produceUntilCut(children, base, width, victim, wire, marker)) { fault.put("pendingIdentity", base); break; }
                    http(endpoint, "FILL_NEW"); Thread.sleep(200);
                }
            }
            awaitFaultMarker(wire, marker);
            fault.put("wireBoundary", marker);
        } else if (repetition == 1) {
            fault.put("mode", "AFTER_VENUE_ACCEPTED");
            http(endpoint, "B1_LOST_ACK");
            for (int j = 0; j < width; j++) children.get(j).startCommand("L5_PLACE " + (base + j));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (http(endpoint, null).path("places").asInt() < base + width - 1 && System.nanoTime() < deadline) Thread.sleep(50);
            JsonNode venue = http(endpoint, null);
            assertEquals(base + width - 1, venue.path("places").asInt());
            fault.set("venueDuring", venue);
        } else {
            produce(children, base, width);
            if (repetition == 2) {
                fault.put("mode", "AFTER_TRADE_COMMIT");
                victim.send("ARM_B4_TRADE_COMMIT");
                http(endpoint, "FILL_NEW");
                // 继续原有有界生产波次，避免其他actor先处理唯一一波后目标永远无新成交。
                while (!Files.readString(victim.log).contains("B4_CUT AFTER_TRADE_COMMIT") && base < 109) {
                    base += width;
                    boolean pendingPlace = produceUntilCut(children, base, width, victim, victim, "B4_CUT AFTER_TRADE_COMMIT");
                    if (pendingPlace) { fault.put("pendingIdentity", base); break; }
                    http(endpoint, "FILL_NEW"); Thread.sleep(200);
                }
                awaitFaultMarker(victim, "B4_CUT AFTER_TRADE_COMMIT");
            } else {
                fault.put("mode", "V51_B");
                Thread.sleep(1100);
                victim.send("ARM_V51_B");
                victim.startCommand("L5_SCAN");
                awaitFaultMarker(victim, "B5_CUT V51_B");
            }
        }
        fault.set("atCut", facts(reader));
        fault.set("during", sample(reader));
        victim.kill(); victim.close();
        fault.put("deathMillis", System.currentTimeMillis()).put("oldDead", !victim.process.isAlive());
        if (databaseFault) {
            wire.send("DROP_PENDING");
            if (repetition != 3) for (int j = 1; j < width; j++) assertEquals("L5_PLACE ACCEPTED", children.get(j).result());
        } else if (repetition == 1) {
            for (int j = 1; j < width; j++) assertEquals("L5_PLACE ACCEPTED", children.get(j).result());
            http(endpoint, "RELEASE_LOST"); http(endpoint, "L5_OPEN");
        }
        var replacement = new B0Processes.Child(L5FaultNqProcessMain.class, dir, "nq-fault-recovery", env);
        children.set(0, replacement);
        replacement.awaitReady();
        fault.put("newPid", replacement.process.pid()).put("newStartMillis", System.currentTimeMillis());
        if (fault.has("pendingIdentity")) {
            String client = "l5" + value(reader, "SELECT current_database()").substring(value(reader, "SELECT current_database()").length() - 20)
                    + String.format("%04d", fault.path("pendingIdentity").asInt());
            long present = number(reader, "SELECT count(*) FROM orders WHERE client_order_id='" + client + "'");
            fault.put("pendingPlaceDurableCount", present);
            // 仅在持久事实证明尚未创建时投喂该逻辑工作；已有Order只由查询和对账恢复。
            if (present == 0) assertEquals("L5_PLACE ACCEPTED", replacement.send("L5_PLACE " + fault.path("pendingIdentity").asInt()));
        }
        replacement.send("L5C_BEGIN");
        replacement.send("L5_PROJECT");
        fault.put("endMillis", System.currentTimeMillis());
        return base;
    }

    /** 同时监视命令回复和故障屏障，不在已被屏障暂停的测试slot上等待回复。 */
    private static boolean produceUntilCut(List<B0Processes.Child> children, int base, int width,
            B0Processes.Child victim, B0Processes.Child observer, String marker) throws Exception {
        Thread.sleep(300);
        long results = Files.readAllLines(victim.log).stream().filter(line -> line.startsWith("B0_RESULT ")).count();
        for (int j = 0; j < width; j++) children.get(j).startCommand("L5_PLACE " + (base + j));
        for (int j = 1; j < width; j++) assertEquals("L5_PLACE ACCEPTED", children.get(j).result());
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            String log = Files.readString(victim.log);
            if (Files.readString(observer.log).contains(marker)) return true;
            if (log.lines().filter(line -> line.startsWith("B0_RESULT ")).count() > results) {
                assertEquals("L5_PLACE ACCEPTED", victim.result()); return false;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("BLOCKED / L5_REPEATED_FAULT_HARNESS_GAP: pending producer or cut missing");
    }

    private static void awaitFaultMarker(B0Processes.Child child, String marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.readString(child.log).contains(marker)) return;
            assertTrue(child.process.isAlive(), "process died before selected cut");
            Thread.sleep(50);
        }
        throw new AssertionError("BLOCKED / L5_REPEATED_FAULT_HARNESS_GAP: cut not reached " + marker);
    }

    private static void produce(List<B0Processes.Child> children, int base, int width) throws Exception {
        Thread.sleep(300);
        for (int j = 0; j < width; j++) children.get(j).startCommand("L5_PLACE " + (base + j));
        for (var child : children) assertEquals("L5_PLACE ACCEPTED", child.result());
    }

    static JsonNode cursor(Connection reader) throws Exception {
        return JSON.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(c))::text,'[]') FROM reconciliation_scan_cursors c WHERE venue='OKX'"));
    }

    private void drain(Connection reader, List<B0Processes.Child> children, int expected, ObjectNode proof) throws Exception {
        int iterations = 0;
        long start = System.nanoTime();
        // 两轮以上游标绕行预算；循环有硬上限，不用任意毫秒latency决定业务P级。
        while (number(reader, BACKLOG) > 0 && iterations < 2 * ((expected + 99) / 100) + 2) {
            children.get(iterations % children.size()).send("L5_RECONCILE"); iterations++;
        }
        proof.put("scanIterations", proof.path("scanIterations").asInt() + iterations);
        proof.put("drainSeconds", proof.path("drainSeconds").asDouble() + (System.nanoTime() - start) / 1e9);
        assertEquals(0, number(reader, BACKLOG), "L5 actionable backlog failed to converge within fair scan budget");
    }

    static ObjectNode sample(Connection reader) throws Exception {
        ObjectNode result = JSON.createObjectNode().put("timeMillis", System.currentTimeMillis());
        result.put("backlog", number(reader, BACKLOG));
        result.put("correctnessRequiredUnresolved", number(reader, UNKNOWN));
        result.put("actionable", result.path("backlog").asLong() - result.path("correctnessRequiredUnresolved").asLong());
        result.put("orders", number(reader, "SELECT count(*) FROM orders"));
        result.put("terminal", number(reader, "SELECT count(*) FROM orders WHERE status='FILLED'"));
        result.put("trades", number(reader, "SELECT count(*) FROM trades"));
        result.put("ledger", number(reader, "SELECT count(*) FROM ledger_entries"));
        result.put("runsSucceeded", number(reader, "SELECT count(*) FROM strategy_runs WHERE status='SUCCEEDED'"));
        result.put("transactions", number(reader, "SELECT xact_commit+xact_rollback FROM pg_stat_database WHERE datname=current_database()"));
        result.put("appConnections", number(reader, "SELECT count(*) FROM pg_stat_activity WHERE usename='nq_b0_app'"));
        result.put("idleInTransaction", number(reader, "SELECT count(*) FROM pg_stat_activity WHERE usename='nq_b0_app' AND state='idle in transaction'"));
        return result;
    }

    static ObjectNode facts(Connection reader) throws Exception {
        ObjectNode result = JSON.createObjectNode();
        for (String table : List.of("orders", "ordinary_place_authorities", "trades", "ledger_entries", "ledger_events", "positions", "account_snapshots", "strategy_runs", "strategy_run_dispatch_work")) {
            result.set(table, JSON.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t")));
        }
        result.set("events", JSON.readTree(value(reader, "SELECT coalesce(jsonb_agg(jsonb_build_object('event_id',event_id,'payload',payload_json->'payload') ORDER BY event_id)::text,'[]') FROM event_store WHERE event_type='TradeExecuted'")));
        return result;
    }

    static JsonNode http(String endpoint, String command) throws Exception {
        B0Fixture.requireVenue(endpoint);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            boolean read = command == null || "METRICS".equals(command);
            var builder = HttpRequest.newBuilder(URI.create(endpoint + (command == null ? "/facts" : read ? "/l5-metrics" : "/control"))).timeout(Duration.ofSeconds(5));
            var response = client.send(read ? builder.GET().build() : builder.POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode()); return JSON.readTree(response.body());
        }
    }

    private static void seedStrategies(B0Fixture fixture, boolean concurrentRun) throws Exception {
        try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
            for (int index = 1; index <= 4; index++) {
                s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot) "
                        + "SELECT 'l5-strategy-" + index + "','l5-strategy-" + index + "','L5 fixture','TEST','OKX',account_id,'SIM',true,"
                        + "'{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"" + (concurrentRun ? "0.1005" : "0.1") + "\"}'::jsonb FROM accounts WHERE account_code='b0-account'");
                s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,enabled,dedup_scope,exchange_code,account_id,trade_env,created_at) "
                        + "SELECT 'l5-schedule-" + index + "','l5-strategy-" + index + "','0 0 0 1 1 *','UTC',true,'SCHEDULE_WINDOW','OKX',account_id,'SIM',date_trunc('year',CURRENT_TIMESTAMP)-INTERVAL '2 years' FROM accounts WHERE account_code='b0-account'");
            }
        }
    }

    static long number(Connection connection, String sql) throws Exception { return Long.parseLong(value(connection, sql)); }
    static String value(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var result = statement.executeQuery(sql)) { assertTrue(result.next()); return result.getString(1); }
        }
    }
}
