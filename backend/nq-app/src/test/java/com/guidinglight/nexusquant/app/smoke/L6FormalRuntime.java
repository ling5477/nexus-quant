package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** 正式模式与短 smoke 共用整个运行路径；只有阶段长度不同。 */
final class L6FormalRuntime {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final L6FormalManifest manifest;
    private final QualificationCapacity capacity;
    private final L6DurationContract duration;
    private final boolean smoke;
    private final List<B0Processes.Child> actors = new ArrayList<>();
    private final List<L6DeterministicPacer.Slot> slots = new ArrayList<>();
    private final List<Long> childClockOffsets = new ArrayList<>();
    private final ObjectNode proof = JSON.createObjectNode();
    private Path dir;
    private long start;
    private int checks;

    L6FormalRuntime(L6FormalManifest manifest, QualificationCapacity capacity, L6DurationContract duration, boolean smoke) {
        this.manifest = manifest; this.capacity = capacity; this.duration = duration; this.smoke = smoke;
    }
    void run() throws Exception {
        dir = B0Processes.root().resolve("backend/nq-app/target/l6-formal/" + UUID.randomUUID()); Files.createDirectories(dir);
        System.out.println("L6_FORMAL_ROOT " + dir);
        proof.put("mode", "FORMAL_L6_A").put("shortSmoke", smoke).put("runId", dir.getFileName().toString())
                .put("formalCalibrationAccepted", true).put("l6AAccepted", false).put("l6Accepted", false)
                .put("HEAD", B0Processes.command("git", "rev-parse", "HEAD").trim()).put("controllerPid", ProcessHandle.current().pid())
                .put("capacityPreflight", "PASS").put("runOrderBudget", capacity.runOrderBudget())
                .put("venueLogicalCapacity", capacity.venueLogicalOrderCapacity()).put("l6SafetyCap", capacity.globalStageSafetyCap());
        proof.set("manifestEntry", manifest.identity()); proof.set("frozenInput", manifest.frozen());
        Files.writeString(dir.resolve("parameters.json"), JSON.writeValueAsString(proof));
        String container = null; long venuePid = 0;
        try {
            try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg)) {
                container = pg.ownedContainerId(); proof.put("container", container);
                var env = B0Processes.cleanEnvironment();
                try (var venue = new B0Processes.Child(L6FormalVenueProcessMain.class, dir, "venue", env)) {
                    venuePid = venue.process.pid(); proof.put("venuePid", venuePid);
                    String endpoint = "http://127.0.0.1:" + venue.ready();
                    env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                    fixture.initialize(true, endpoint, env); L6ActiveStabilityTest.seed(fixture);
                    // 保留真实 scanner 和分钟 cron 配置，但 fixture 的 schedule 不拥有订单 admission。
                    try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var statement = owner.createStatement()) {
                        assertEquals(2, statement.executeUpdate("UPDATE strategy_schedules SET enabled=false WHERE schedule_job_id IN ('l6-schedule-1','l6-schedule-2')"));
                    }
                    try {
                        for (int i = 0; i < 2; i++) actors.add(new B0Processes.Child(L6NqProcessMain.class, dir, "nq-" + i, env).awaitReady());
                        // 子进程时钟样本发生在父进程收到响应之前；该映射只会提前截止，不假设跨JVM同一时钟原点。
                        for (var actor : actors) {
                            long childNanos = Long.parseLong(actor.send("L6_CLOCK"));
                            childClockOffsets.add(childNanos - System.nanoTime());
                        }
                        proof.set("paper", JSON.readTree(actors.getFirst().send("L6_PAPER"))); http(endpoint, "L5_OPEN");
                        try (var reader = fixture.checker(); var resourceReader = fixture.checker();
                             var resources = new L6RuntimeResources(resourceReader, dir, actors, venue, endpoint, container, true)) {
                            proof.put("postgresVersion", value(reader, "SHOW server_version")).put("schema", value(reader,"SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                            assertEquals("51", proof.path("schema").asText());
                            reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); reader.setAutoCommit(false);
                            start = System.nanoTime(); proof.put("startedAt", Instant.now().toString()).put("formalTimerStarted", true);
                            try (var sampler = resources.sampler(duration, start, dir.resolve("resources.ndjson"))) {
                                sampler.start(); drive(reader, endpoint, sampler); sampler.requireComplete();
                                var end = checkpoint(reader, endpoint, "FINAL"); reader.commit(); proof.set("final", end);
                                assertEquals(0, end.path("backlog").asInt()); assertEquals(0, end.path("idleInTransaction").asInt());
                                var finalActors = proof.putArray("finalActorMetrics");
                                for (var actor : actors) {
                                    var metrics = JSON.readTree(actor.send("L6_METRICS")); finalActors.add(metrics);
                                    assertEquals(0, metrics.path("pending").asInt()); assertEquals(0, metrics.path("commandQueue").path("queueSize").asInt());
                                    assertEquals(0, metrics.path("candidateAge").path("eligibleCandidateCount").asInt());
                                    assertTrue(metrics.path("candidateAge").path("oldestCandidateAgeMillis").isNull());
                                    assertTrue(metrics.has("validationQualification")); assertTrue(metrics.path("tickCompleted").asInt() > 0);
                                }
                                assertEquals(0, http(endpoint, null).path("executorQueue").asInt());
                                proof.set("sampling", sampler.summary());
                            }
                        }
                    } finally { B0Processes.closeChildren(actors); }
                }
            }
            assertFalse(ProcessHandle.of(venuePid).map(ProcessHandle::isAlive).orElse(false));
            assertTrue(B0Processes.command("docker", "ps", "-a", "--filter", "id=" + container, "--format", "{{.ID}}").isBlank());
            proof.put("cleanup", "PASS").put("ownedSurvivors", 0).put("result", smoke ? "FORMAL_MODE_SMOKE_PASS" : "FORMAL_MEASURED_PENDING_QUALIFICATION");
        } catch (Exception | AssertionError error) { proof.put("result", "FAILED").put("failure", error.toString()); throw error; }
        finally {
            manifest.verifyUnchanged(); proof.set("manifestExit", manifest.identity());
            proof.put("ownedNqRemaining", actors.stream().filter(a -> a.process.isAlive()).count());
            Files.writeString(dir.resolve("proof.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
    }
    private void drive(Connection reader, String endpoint, L6ResourceSampler sampler) throws Exception {
        var pacer = new L6DeterministicPacer(System::nanoTime, start, manifest.intervalNanos(), duration);
        long nextObserver = 0, nextCheck = 0, drainOrders = -1;
        int scans = 0; String phase = ""; long phaseStart = 0;
        while (elapsed() < duration.total()) {
            sampler.checkHealthy(); long now = elapsed(); String current = duration.phase(now).name();
            if (!current.equals(phase)) {
                if (!phase.isEmpty()) timing(phase, phaseStart, now);
                phase = current; phaseStart = now;
                if (current.equals("DRAIN")) { drainOrders = number(reader, "SELECT count(*) FROM orders"); reader.commit(); proof.put("ordersAtDrainStart", drainOrders); }
            }
            long orders = number(reader, "SELECT count(*) FROM orders"); long backlog = sample(reader).path("backlog").asLong(); reader.commit();
            pacer.poll(backlog > 0, slot -> {
                capacity.reserve(orders, 1);
                int actorIndex = (int) (slot % 2);
                long childDeadline = start + duration.activeEnd() + childClockOffsets.get(actorIndex);
                var response = JSON.readTree(actors.get(actorIndex).send("L6_EMIT " + slot + " " + childDeadline));
                assertEquals(slot, response.path("slotIndex").asLong()); return response.path("logicalOrderId").asText();
            }, slot -> { slots.add(slot); append("pacing.ndjson", JSON.valueToTree(slot)); });
            if (elapsed() >= nextObserver) {
                long before = number(reader, "SELECT count(*) FROM orders"); reader.commit();
                for (var actor : actors) {
                    var observation = JSON.createObjectNode().put("elapsedNanos", elapsed()).put("pid", actor.process.pid());
                    observation.set("scan", JSON.readTree(actor.send("L6_OBSERVER_SCAN"))); append("scheduler.ndjson", observation); scans++;
                }
                assertEquals(before, number(reader, "SELECT count(*) FROM orders")); reader.commit();
                http(endpoint, "FILL"); for (var actor : actors) actor.send("L6_RECONCILE");
                nextObserver = (elapsed() / 5_000_000_000L + 1) * 5_000_000_000L;
            }
            if (elapsed() >= nextCheck && orders > 0) {
                http(endpoint, "FILL"); for (var actor : actors) actor.send("L6_RECONCILE");
                var point = checkpoint(reader, endpoint, current); reader.commit();
                point.put("completedObservedElapsedNanos", elapsed()); append("progress.ndjson", point);
                assertTrue(point.path("transactions").asLong() <= 1_000_000);
                nextCheck = elapsed() + 30_000_000_000L;
            }
            if (drainOrders >= 0) { assertEquals(drainOrders, number(reader, "SELECT count(*) FROM orders")); reader.commit(); }
            long next = Math.min(duration.total(), Math.min(nextObserver, Math.min(nextCheck, pacer.nextElapsed())));
            next = Math.min(next, current.equals("WARMUP") ? duration.warmupNanos() : current.equals("ACTIVE") ? duration.activeEnd() : duration.total());
            long wait = start + next - System.nanoTime();
            if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
        }
        timing(phase, phaseStart, elapsed());
        proof.put("schedulerScans", scans).put("newOrdersDuringDrain", number(reader,"SELECT count(*) FROM orders") - drainOrders); reader.commit();
        var emitted = slots.stream().filter(s -> s.decision().equals("EMITTED")).toList(); assertTrue(emitted.size() >= 3);
        long minGap = Long.MAX_VALUE;
        for (int i = 1; i < emitted.size(); i++) {
            assertTrue(emitted.get(i).actualElapsed() - emitted.get(i - 1).actualElapsed() >= manifest.intervalNanos());
            minGap = Math.min(minGap, emitted.get(i).actualElapsed() - emitted.get(i - 1).actualElapsed());
        }
        proof.put("emittedCount", emitted.size()).put("minimumDispatchIntervalNanos", minGap)
                .put("maximumInstantaneousProducerRate", 1e9 / minGap).put("pacingUnits", "nanoseconds")
                .put("noCatchUp", true).put("drainEmitsZero", true).put("schedulerIsWorkloadAuthority", false);
        assertTrue(1e9 / minGap <= manifest.frozen().path("finalL6ArrivalRate").asDouble());
    }
    private ObjectNode checkpoint(Connection reader, String endpoint, String phase) throws Exception {
        return L6BusinessCheckpoint.verify(reader, endpoint, actors, dir, phase, ++checks, false, capacity.runOrderBudget(), slots);
    }
    private void timing(String phase, long from, long to) { proof.withArray("phases").addObject().put("phase", phase).put("startElapsedNanos", from).put("endElapsedNanos", to).put("durationNanos", to - from); }
    private long elapsed() { return System.nanoTime() - start; }
    private void append(String name, com.fasterxml.jackson.databind.JsonNode value) throws Exception { Files.writeString(dir.resolve(name), JSON.writeValueAsString(value) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
}
