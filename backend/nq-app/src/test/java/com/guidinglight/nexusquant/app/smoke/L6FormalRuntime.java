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

/** 正式10/40/10读取双合同；storage calibration沿用自己的时长与容量边界。 */
final class L6FormalRuntime {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final L6FormalManifest manifest;
    private final QualificationCapacity capacity;
    private final L6DurationContract duration;
    private final boolean smoke;
    private final L6StorageCapacityGuard storageGuard;
    private final List<B0Processes.Child> actors = new ArrayList<>();
    private final List<L6DeterministicPacer.Slot> slots = new ArrayList<>();
    private final List<Long> childClockOffsets = new ArrayList<>();
    private final ObjectNode proof = JSON.createObjectNode();
    private Path dir;
    private long start;
    private int checks;
    private L6StoragePhaseController phaseController;
    private boolean jitterInjected;
    private L6PgCapacityContract pgContract;
    private L6PgProjectionGuard projection;
    private final L6ResourceFileLifecycle resourceFileLifecycle = new L6ResourceFileLifecycle();

    L6FormalRuntime(L6FormalManifest manifest, QualificationCapacity capacity, L6DurationContract duration, boolean smoke) {
        this.manifest = manifest; this.capacity = capacity; this.duration = duration; this.smoke = smoke;
        this.storageGuard = null;
    }
    L6FormalRuntime(L6FormalManifest manifest, QualificationCapacity capacity, L6StorageCalibrationContract contract) {
        this.manifest = manifest; this.capacity = capacity; this.duration = contract.timing(); this.smoke = contract.smoke();
        this.storageGuard = new L6StorageCapacityGuard();
    }
    private boolean storage() { return storageGuard != null; }
    void run() throws Exception {
        dir = B0Processes.root().resolve((storage() ? "backend/nq-app/target/l6-storage-calibration/" : "backend/nq-app/target/l6-formal/") + UUID.randomUUID()); Files.createDirectories(dir);
        if (storage()) Files.createDirectories(dir.resolve("phase-boundaries"));
        if (Boolean.getBoolean("nq.l6.storage.timing.jitter") && (!storage() || !smoke)) {
            throw new IllegalArgumentException("L6_TIMING_JITTER_REQUIRES_STORAGE_SMOKE");
        }
        System.out.println("L6_FORMAL_ROOT " + dir);
        proof.put("mode", storage() ? L6StorageCalibrationContract.MODE : "FORMAL_L6_A").put("shortSmoke", smoke).put("runId", dir.getFileName().toString())
                .put("formalCalibrationAccepted", true).put("l6AAccepted", false).put("l6Accepted", false)
                .put("HEAD", B0Processes.command("git", "rev-parse", "HEAD").trim()).put("controllerPid", ProcessHandle.current().pid())
                .put("capacityPreflight", "PASS").put("runOrderBudget", capacity.runOrderBudget())
                .put("venueLogicalCapacity", capacity.venueLogicalOrderCapacity()).put("l6SafetyCap", capacity.globalStageSafetyCap());
        proof.put("storageCalibrationAccepted", false);
        proof.set("manifestEntry", manifest.identity()); proof.set("frozenInput", manifest.frozen());
        Files.writeString(dir.resolve("parameters.json"), JSON.writeValueAsString(proof));
        String container = null; long venuePid = 0;
        try {
            if (!storage()) {
                if (smoke || duration.total() != 3_600_000_000_000L || duration.warmupNanos() != 600_000_000_000L
                        || duration.activeEnd() != 3_000_000_000_000L) throw new IllegalStateException("BLOCKED / L6_CAPACITY_SCOPE_REQUIRES_10_40_10_USE_SEPARATE_PG_FIXTURE_SMOKE");
                proof.put("pgStarted", false).put("venueStarted", false).put("nqStarted", false).put("formalTimerStarted", false).put("orders", 0);
                pgContract = L6PgCapacityContract.committed();
                proof.set("capacityContractEntry", pgContract.identity());
                var entry = L6HostMemoryPreflight.observe();
                proof.put("availableHostMemoryAtEntry", entry.availableBytes()).put("totalQualificationOwnedBudget", L6HostMemoryPreflight.budget(pgContract));
                proof.set("hostMemoryPreflight", L6HostMemoryPreflight.verify(pgContract, entry));
                projection = new L6PgProjectionGuard(pgContract);
            }
            try (var pg = storage() ? B0Processes.Pg.startBounded() : B0Processes.Pg.startL6(pgContract); var fixture = B0Fixture.create(pg)) {
                proof.put("pgStarted", true);
                container = pg.ownedContainerId(); proof.put("container", container);
                var env = B0Processes.cleanEnvironment();
                try (var venue = new B0Processes.Child(L6FormalVenueProcessMain.class, dir, "venue", env)) {
                    venuePid = venue.process.pid(); proof.put("venuePid", venuePid);
                    proof.put("venueStarted", true);
                    String endpoint = "http://127.0.0.1:" + venue.ready();
                    env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                    fixture.initialize(true, endpoint, env); L6ActiveStabilityTest.seed(fixture);
                    // 保留真实 scanner 和分钟 cron 配置，但 fixture 的 schedule 不拥有订单 admission。
                    try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var statement = owner.createStatement()) {
                        assertEquals(2, statement.executeUpdate("UPDATE strategy_schedules SET enabled=false WHERE schedule_job_id IN ('l6-schedule-1','l6-schedule-2')"));
                    }
                    try {
                        for (int i = 0; i < 2; i++) actors.add(new B0Processes.Child(L6NqProcessMain.class, dir, "nq-" + i, env).awaitReady());
                        proof.put("nqStarted", true);
                        // 子进程时钟样本发生在父进程收到响应之前；该映射只会提前截止，不假设跨JVM同一时钟原点。
                        for (var actor : actors) {
                            long childNanos = Long.parseLong(actor.send("L6_CLOCK"));
                            childClockOffsets.add(childNanos - System.nanoTime());
                        }
                        proof.set("paper", JSON.readTree(actors.getFirst().send("L6_PAPER"))); http(endpoint, "L5_OPEN");
                        try (var reader = fixture.checker(); var resourceReader = fixture.checker();
                             var boundaryReader = storage() ? fixture.checker() : null;
                             var resources = new L6RuntimeResources(resourceReader, dir, actors, venue, endpoint, container, true, storage(), resourceFileLifecycle);
                             var boundaryResources = storage() ? new L6RuntimeResources(boundaryReader, dir, actors, venue, endpoint, container, true, true, resourceFileLifecycle) : null) {
                            proof.put("postgresVersion", value(reader, "SHOW server_version")).put("schema", value(reader,"SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                            assertEquals("51", proof.path("schema").asText());
                            reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); reader.setAutoCommit(false);
                            if (!storage()) try (var statement = reader.createStatement()) { statement.execute("SET statement_timeout='2000ms'"); reader.commit(); }
                            start = System.nanoTime(); proof.put("startedAt", Instant.now().toString()).put("formalTimerStarted", !storage()).put("storageTimerStarted", storage());
                            L6SamplingSchedule schedule = storage() ? new L6SamplingSchedule() {
                                public long total() { return duration.total(); }
                                public String samplePhase(long elapsed) {
                                    return storageGuard.stopped() ? "DRAIN" : duration.phase(elapsed) == L6DurationContract.Phase.ACTIVE ? "MEASUREMENT" : duration.samplePhase(elapsed);
                                }
                            } : new L6SamplingSchedule() {
                                public long total() { return duration.total(); }
                                public String samplePhase(long elapsed) { return projection.stopped() ? "DRAIN" : duration.samplePhase(elapsed); }
                            };
                            try (var sampler = resources.sampler(schedule, start, dir.resolve("resources.ndjson"));
                                 var boundarySampler = storage() ? boundaryResources.sampler(schedule, start, dir.resolve("phase-boundaries/resources.ndjson")) : null;
                                 var controller = storage() ? new L6StoragePhaseController(duration, System::nanoTime, start,
                                         boundarySampler::boundaryAt, dir.resolve("phase-timing.json")) : null) {
                                phaseController = controller;
                                if (storage()) {
                                    sampler.observeWith(record -> storageGuard.observe(record, elapsed()));
                                    boundarySampler.observeWith(record -> storageGuard.observe(record, elapsed()));
                                    controller.start();
                                    sampler.sample();
                                } else {
                                    sampler.observeWith(record -> projection.observe(record, elapsed()));
                                    sampler.sample();
                                }
                                sampler.start(); drive(reader, endpoint, sampler);
                                if (storage()) controller.awaitEnd();
                                if (!capacityStopped()) sampler.requireComplete();
                                if (storage()) proof.set("storageGuard", storageGuard.evidence());
                                if (!capacityStopped()) {
                                var end = checkpoint(reader, endpoint, "FINAL"); reader.commit(); proof.set("final", end);
                                if (storage()) {
                                    long complete = boundarySampler.latest().path("sources").path("postgres").path("values").path("fullChainCompleted").asLong();
                                    assertTrue(complete > 0);
                                    assertEquals(end.path("oracle").path("orders").asLong(), complete);
                                    var boundaryNames = new java.util.HashSet<String>();
                                    for (var row : boundarySampler.summary().path("samples")) {
                                        row.path("boundaryNames").forEach(name -> boundaryNames.add(name.asText()));
                                    }
                                    assertEquals(java.util.Set.of("WARMUP_END", "MEASUREMENT_START", "MEASUREMENT_END", "DRAIN_START", "DRAIN_END"), boundaryNames);
                                    assertEquals(5, boundarySampler.summary().path("boundarySampleCount").asInt());
                                }
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
                                }
                                proof.set("sampling", sampler.summary());
                                if (storage()) proof.set("boundarySampling", boundarySampler.summary());
                            }
                        }
                    } finally { B0Processes.closeChildren(actors); }
                }
            }
            assertFalse(ProcessHandle.of(venuePid).map(ProcessHandle::isAlive).orElse(false));
            assertTrue(B0Processes.command("docker", "ps", "-a", "--filter", "id=" + container, "--format", "{{.ID}}").isBlank());
            proof.put("cleanup", "PASS").put("ownedSurvivors", 0).put("result", storage()
                    ? storageGuard.stopped() ? "BLOCKED / STORAGE_CALIBRATION_CAPACITY_AT_RISK"
                    : smoke ? "STORAGE_CALIBRATION_SMOKE_PASS" : "STORAGE_CALIBRATION_MEASURED_PENDING_QUALIFICATION"
                    : capacityStopped() ? L6PgProjectionGuard.RESULT : "FORMAL_MEASURED_PENDING_QUALIFICATION");
        } catch (Exception | AssertionError error) { proof.put("result", storage() && storageGuard.stopped() ? "BLOCKED / STORAGE_CALIBRATION_CAPACITY_AT_RISK"
                : storage() ? L6StoragePhaseController.timingFailureResult(error) : capacityStopped() ? L6PgProjectionGuard.RESULT
                : error.getMessage() != null && error.getMessage().startsWith("BLOCKED /") ? error.getMessage() : "FAILED").put("failure", error.toString()); throw error; }
        finally {
            if (phaseController != null) proof.set("phaseTiming", phaseController.summary());
            if (projection != null) proof.set("storageProjectionGuard", projection.evidence());
            persistExit(dir.resolve("proof.json"), proof, () -> {
                if (pgContract != null) { pgContract.verifyUnchanged(); proof.set("capacityContractExit", pgContract.identity()); }
                manifest.verifyUnchanged(); proof.set("manifestExit", manifest.identity());
            }, actors.stream().filter(a -> a.process.isAlive()).count());
        }
    }
    @FunctionalInterface interface VerifyExit { void run() throws Exception; }
    static void persistExit(Path path, ObjectNode proof, VerifyExit verify, long survivors) throws Exception {
        // 身份漂移仍须留下最终失败和清理事实，写盘后继续抛错，不能升级为PASS。
        try { verify.run(); }
        catch (Exception failure) {
            proof.put("result", "BLOCKED / L6_FROZEN_INPUT_IDENTITY_DRIFT").put("identityFailure", failure.toString());
            throw failure;
        } finally {
            proof.put("ownedNqRemaining", survivors);
            Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
    }
    private void drive(Connection reader, String endpoint, L6ResourceSampler sampler) throws Exception {
        var pacer = new L6DeterministicPacer(System::nanoTime, start, manifest.intervalNanos(), duration,
                () -> storage() ? phaseController.producerAllowed() : projection.producerAllowed());
        long nextObserver = 0, nextCheck = 0, drainOrders = -1;
        int scans = 0; String phase = ""; long phaseStart = 0;
        while (elapsed() < duration.total()) {
            try {
            sampler.checkHealthy();
            if (storage()) {
                phaseController.checkHealthy();
                if (storageGuard.stopped()) phaseController.capacityStop();
                injectCheckpointJitter();
                if (phaseController.phase().equals("DRAIN") && !phaseController.drainReady()) {
                    TimeUnit.MILLISECONDS.sleep(25); continue;
                }
            }
            long now = elapsed();
            String current = storage() ? phaseController.phase() : projection.stopped() ? "DRAIN" : duration.phase(now).name();
            if (storage() && current.equals("ACTIVE")) current = "MEASUREMENT";
            if (!current.equals(phase)) {
                if (!phase.isEmpty()) timing(phase, phaseStart, now);
                phase = current; phaseStart = now;
                if (current.equals("DRAIN")) { drainOrders = number(reader, "SELECT count(*) FROM orders"); reader.commit(); proof.put("ordersAtDrainStart", drainOrders); }
            }
            if (storage() && storageGuard.drainExpired(elapsed())) break;
            if (!storage() && projection.drainExpired(elapsed())) break;
            long orders = number(reader, "SELECT count(*) FROM orders"); long backlog = sample(reader).path("backlog").asLong(); reader.commit();
            try {
            if (!storage() || storageGuard.producerAllowed()) pacer.poll(backlog > 0, slot -> {
                capacity.reserve(orders, 1);
                int actorIndex = (int) (slot % 2);
                long childDeadline = start + duration.activeEnd() + childClockOffsets.get(actorIndex);
                String command = "L6_EMIT " + slot + " " + childDeadline;
                var actor = actors.get(actorIndex);
                String reply;
                if (storage()) {
                    phaseController.admit(slot, () -> storageGuard.produce(() -> actor.startCommand(command)));
                    reply = actor.resultBefore(System.nanoTime() + 5_000_000_000L);
                } else {
                    projection.produce(() -> actor.startCommand(command));
                    reply = actor.resultBefore(System.nanoTime() + 5_000_000_000L);
                }
                var response = JSON.readTree(reply);
                assertEquals(slot, response.path("slotIndex").asLong()); return response.path("logicalOrderId").asText();
            }, slot -> { slots.add(slot); append("pacing.ndjson", JSON.valueToTree(slot)); });
            } catch (L6StorageCapacityGuard.ProducerStopped stopped) { continue; }
              catch (L6PgProjectionGuard.ProducerStopped stopped) { continue; }
            if (elapsed() >= nextObserver) {
                long before = number(reader, "SELECT count(*) FROM orders"); reader.commit();
                for (var actor : actors) {
                    var observation = JSON.createObjectNode().put("elapsedNanos", elapsed()).put("pid", actor.process.pid());
                    observation.set("scan", JSON.readTree(runtimeCommand(actor, "L6_OBSERVER_SCAN"))); append("scheduler.ndjson", observation); scans++;
                }
                assertEquals(before, number(reader, "SELECT count(*) FROM orders")); reader.commit();
                runtimeFill(endpoint); for (var actor : actors) runtimeCommand(actor, "L6_RECONCILE");
                nextObserver = (elapsed() / 5_000_000_000L + 1) * 5_000_000_000L;
            }
            if (elapsed() >= nextCheck && orders > 0 && !capacityStopped() && (!storage() || (!storageGuard.stopped()
                    && nextBoundary(elapsed()) - elapsed() > 8_000_000_000L))) {
                runtimeFill(endpoint); for (var actor : actors) runtimeCommand(actor, "L6_RECONCILE");
                try {
                    var point = checkpoint(reader, endpoint, current); reader.commit();
                    point.put("completedObservedElapsedNanos", elapsed()); append("progress.ndjson", point);
                    assertTrue(point.path("transactions").asLong() <= 1_000_000);
                } catch (L6BusinessCheckpoint.StoragePending pending) { reader.rollback(); }
                nextCheck = elapsed() + 30_000_000_000L;
            }
            if (drainOrders >= 0) { assertEquals(drainOrders, number(reader, "SELECT count(*) FROM orders")); reader.commit(); }
            if (capacityStopped()) {
                if (drainOrders >= 0) assertEquals(drainOrders, number(reader, "SELECT count(*) FROM orders"));
                reader.commit(); TimeUnit.MILLISECONDS.sleep(100); continue;
            }
            long next = Math.min(duration.total(), Math.min(nextObserver, Math.min(nextCheck, pacer.nextElapsed())));
            next = Math.min(next, current.equals("WARMUP") ? duration.warmupNanos() : (current.equals("ACTIVE") || current.equals("MEASUREMENT")) ? duration.activeEnd() : duration.total());
            long wait = start + next - System.nanoTime();
            if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
            } catch (L6StoragePhaseController.BoundaryPending pending) {
                reader.rollback(); TimeUnit.MILLISECONDS.sleep(25);
            } catch (L6StorageCapacityGuard.DrainComplete complete) { break; }
              catch (L6PgProjectionGuard.DrainComplete complete) { break; }
        }
        if (storage()) {
            // 风险可在observer调用中触发；紧急退出也必须先登记零时长drain及真实订单基线。
            if (storageGuard.stopped() && drainOrders < 0) {
                long transition = elapsed();
                phaseController.capacityStop();
                drainOrders = number(reader, "SELECT count(*) FROM orders"); reader.commit();
                proof.put("ordersAtDrainStart", drainOrders);
                if (!phase.isEmpty()) timing(phase, phaseStart, transition);
                phase = "DRAIN"; phaseStart = transition;
            }
            if (storageGuard.stopped()) phaseController.endCapacityDrain();
            proof.set("storageGuard", storageGuard.evidence());
        }
        if (!storage() && projection.stopped() && drainOrders < 0) {
            drainOrders = number(reader, "SELECT count(*) FROM orders"); reader.commit();
            proof.put("ordersAtDrainStart", drainOrders);
            timing(phase, phaseStart, elapsed()); phase = "DRAIN"; phaseStart = elapsed();
        }
        timing(phase, phaseStart, elapsed());
        proof.put("schedulerScans", scans).put("newOrdersDuringDrain", number(reader,"SELECT count(*) FROM orders") - drainOrders); reader.commit();
        if (capacityStopped()) return;
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
        if (storage()) phaseController.requireBusinessDispatchReady();
        return L6BusinessCheckpoint.verify(reader, endpoint, actors, dir, phase, ++checks, false, capacity.runOrderBudget(), slots,
                storage() ? L6StorageCalibrationContract.MODE : "FORMAL_L6_A", storage() ? () -> false : projection::stopped);
    }
    private void timing(String phase, long from, long to) { proof.withArray("phases").addObject().put("phase", phase).put("startElapsedNanos", from).put("endElapsedNanos", to).put("durationNanos", to - from); }
    private String runtimeCommand(B0Processes.Child actor, String command) throws Exception {
        if (!storage()) {
            if (projection.drainExpired(elapsed())) throw new L6PgProjectionGuard.DrainComplete();
            actor.startCommand(command);
            return actor.resultBefore(System.nanoTime() + Math.min(5_000_000_000L,
                    projection.stopped() ? Math.max(1, projection.stoppedAt() + L6PgProjectionGuard.DRAIN_LIMIT - elapsed()) : 5_000_000_000L));
        }
        if (storageGuard.drainExpired(elapsed())) throw new L6StorageCapacityGuard.DrainComplete();
        phaseController.requireBusinessDispatchReady();
        actor.startCommand(command);
        return actor.resultBefore(System.nanoTime() + 5_000_000_000L);
    }
    private void runtimeFill(String endpoint) throws Exception {
        if (storage()) phaseController.requireBusinessDispatchReady();
        if (!storage() && projection.drainExpired(elapsed())) throw new L6PgProjectionGuard.DrainComplete();
        if (!storage() && projection.stopped() && projection.stoppedAt() + L6PgProjectionGuard.DRAIN_LIMIT - elapsed() < 5_000_000_000L) throw new L6PgProjectionGuard.DrainComplete();
        http(endpoint, "FILL");
    }
    private boolean capacityStopped() { return storage() ? storageGuard.stopped() : projection != null && projection.stopped(); }
    private long nextBoundary(long elapsed) {
        return elapsed < duration.warmupNanos() ? duration.warmupNanos() : elapsed < duration.activeEnd() ? duration.activeEnd() : duration.total();
    }
    /** 仅显式100秒smoke阻塞检查点通道；不改变periodic节拍或任何正式参数。 */
    private void injectCheckpointJitter() throws Exception {
        if (!Boolean.getBoolean("nq.l6.storage.timing.jitter") || jitterInjected
                || elapsed() < duration.activeEnd() - 5_000_000_000L) return;
        jitterInjected = true;
        var evidence = JSON.createObjectNode().put("kind", "CHECKPOINT_CHANNEL_JITTER")
                .put("startedElapsedNanos", elapsed()).put("measurementEndNanos", duration.activeEnd());
        while (elapsed() < duration.activeEnd() + 4_000_000_000L) TimeUnit.MILLISECONDS.sleep(25);
        evidence.put("endedElapsedNanos", elapsed()).put("phaseAfter", phaseController.phase())
                .put("producerAllowedAfter", phaseController.producerAllowed());
        Files.writeString(dir.resolve("timing-jitter.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
    private long elapsed() { return System.nanoTime() - start; }
    private void append(String name, com.fasterxml.jackson.databind.JsonNode value) throws Exception { Files.writeString(dir.resolve(name), JSON.writeValueAsString(value) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
}
