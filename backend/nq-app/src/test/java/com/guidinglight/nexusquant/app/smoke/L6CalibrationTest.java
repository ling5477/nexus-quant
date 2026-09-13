package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.time.Instant;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.http;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.sample;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.value;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.number;

/** 独立校准入口复用正式actor、采集器和业务oracle；不接受任何故障控制参数。 */
@EnabledIfSystemProperty(named = "nq.l6.calibration", matches = "true")
class L6CalibrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final L6CalibrationContract contract = new L6CalibrationContract(Boolean.getBoolean("nq.l6.calibration.smoke"));
    private final List<B0Processes.Child> actors = new ArrayList<>();
    private final L6CalibrationEvidence evidence = new L6CalibrationEvidence();
    private final ObjectNode proof = JSON.createObjectNode();
    private Path dir;
    private long started;
    private int checks;
    private L6ResourceSampler sampler;

    @Test void calibration() throws Exception {
        assertTrue(System.getProperty("nq.l5.fault", "NONE").equals("NONE"));
        assertTrue(!Boolean.getBoolean("nq.l6") && !Boolean.getBoolean("nq.l5.kill"));
        dir = B0Processes.root().resolve("backend/nq-app/target/l6-calibration/" + UUID.randomUUID());
        Files.createDirectories(dir);
        System.out.println("L6_CALIBRATION_ROOT " + dir);
        proof.put("mode", contract.mode()).put("smoke", contract.smoke()).put("runId", dir.getFileName().toString())
                .put("HEAD", B0Processes.command("git", "rev-parse", "HEAD").trim())
                .put("tree", B0Processes.command("git", "rev-parse", "HEAD^{tree}").trim())
                .put("candidateKind", "HEAD_PLUS_RECORDED_TEST_HARNESS")
                .put("warmupRequiredSeconds", TimeUnit.NANOSECONDS.toSeconds(contract.warmup()))
                .put("measurementRequiredSeconds", TimeUnit.NANOSECONDS.toSeconds(contract.measurement()))
                .put("samplerImplementation", L6ResourceSampler.class.getName())
                .put("collectorImplementation", L6RuntimeResources.class.getName())
                .put("orderBudget", 600).put("producerCadenceSeconds", 5).put("strategies", 2)
                .put("offeredPattern", "two legitimate strategy windows per five seconds; bounded input, not final L6 rate")
                .put("controllerPid", ProcessHandle.current().pid()).put("formalCalibrationAccepted", false)
                .put("L6Accepted", false).put("faults", 0).put("restarts", 0);
        Files.writeString(dir.resolve("parameters.json"), JSON.writeValueAsString(proof));
        int pgPort = 0, venuePort = 0; long venuePid = 0; String container = null;
        try {
            try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(L5VenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                container = pg.ownedContainerId(); pgPort = URI.create(pg.ownedUrl().substring(5)).getPort();
                venuePid = venue.process.pid(); venuePort = Integer.parseInt(venue.ready());
                String endpoint = "http://127.0.0.1:" + venuePort;
                var env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                fixture.initialize(true, endpoint, env); L6ActiveStabilityTest.seed(fixture, true);
                proof.put("database", fixture.name()).put("container", container).put("venuePid", venuePid);
                try {
                    for (int i = 0; i < 2; i++) actors.add(new B0Processes.Child(L6NqProcessMain.class, dir, "nq-" + i, env).awaitReady());
                    proof.set("paper", JSON.readTree(actors.getFirst().send("L6_PAPER")));
                    http(endpoint, "L5_OPEN");
                    try (var reader = fixture.checker(); var resourceReader = fixture.checker();
                         var resources = new L6RuntimeResources(resourceReader, dir, actors, venue, endpoint, container)) {
                        proof.put("postgresVersion", value(reader, "SHOW server_version"));
                        proof.put("schema", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                        assertEquals("51", proof.path("schema").asText());
                        reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); reader.setAutoCommit(false);
                        started = System.nanoTime(); proof.put("startedAt", Instant.now().toString());
                        sampler = resources.sampler(contract, started, dir.resolve("resource-samples.ndjson"));
                        try (var sampling = sampler) {
                            sampling.start();
                            phase(reader, endpoint, contract.warmup(), "WARMUP");
                            long warmEnd = elapsed();
                            if (warmEnd > millis(contract.warmup())) evidence.pause(millis(contract.warmup()), warmEnd, "BOUNDARY");
                            phase(reader, endpoint, contract.total(), "MEASUREMENT");
                            sampling.requireComplete();
                        }
                        // 清理期不属于测量分母，仍保留消费者并核对最终持久事实。
                        long drainStart = elapsed();
                        var terminal = L6BusinessCheckpoint.verify(reader, endpoint, actors, dir, "CLEANUP", ++checks, true);
                        reader.commit();
                        for (var actor : actors) {
                            var metrics = JSON.readTree(actor.send("L6_METRICS"));
                            assertEquals(0, metrics.path("pending").asInt());
                            assertEquals(0, metrics.path("candidateAge").path("eligibleCandidateCount").asInt());
                            assertTrue(metrics.path("candidateAge").path("oldestCandidateAgeMillis").isNull());
                            assertTrue(metrics.has("validationQualification"));
                            proof.withArray("cleanupActorMetrics").add(metrics);
                        }
                        assertEquals(0, number(resourceReader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state LIKE 'idle in transaction%'"));
                        proof.set("terminal", terminal); proof.put("cleanupDrainMillis", elapsed() - drainStart);
                        proof.set("rateEvidence", evidence.export(contract, elapsed(), 0, 0));
                        assertTrue(proof.path("rateEvidence").path("rawRateEvidenceValid").asBoolean(), "CALIBRATION_RATE_EVIDENCE_UNAVAILABLE");
                        proof.set("resourceSampling", sampler.summary());
                        proof.put("mandatoryMissingCount", 0).put("samplingViolations", 0).put("checkpointCount", checks);
                    }
                } finally { B0Processes.closeChildren(actors); }
            }
            assertTrue(ProcessHandle.of(venuePid).map(p -> !p.isAlive()).orElse(true));
            L6ActiveStabilityTest.released(pgPort); L6ActiveStabilityTest.released(venuePort);
            assertTrue(B0Processes.command("docker", "ps", "-a", "--filter", "id=" + container, "--format", "{{.ID}}").isBlank());
            proof.put("cleanup", "PASS").put("ownedSurvivors", 0)
                    .put("result", contract.smoke() ? "CALIBRATION_SMOKE_MEASURED" : "CALIBRATION_RAW_MEASURED_PENDING_ACCEPTANCE");
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAILED").put("failure", failure.toString()); throw failure;
        } finally {
            proof.put("ownedNqRemaining", actors.stream().filter(a -> a.process.isAlive()).count());
            if (sampler != null) proof.set("resourceSampling", sampler.summary());
            Files.writeString(dir.resolve("calibration-run.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
        String analysis = B0Processes.command("python", "-X", "utf8", B0Processes.root().resolve(
                "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/l6_calibration_analyzer.py").toString(), dir.toString());
        System.out.println("L6_CALIBRATION_ANALYSIS " + analysis);
    }

    private void phase(Connection reader, String endpoint, long endNanos, String phase) throws Exception {
        long phaseStart = elapsed();
        while (System.nanoTime() - started < endNanos) {
            sampler.checkHealthy(); long tick = System.nanoTime();
            var before = sample(reader); reader.commit();
            evidence.backlog(before.path("actionable").asLong());
            boolean pressure = before.path("actionable").asLong() > 0;
            long pauseStart = elapsed();
            if (!pressure) {
                for (var actor : actors) actor.startCommand("L6_SCAN");
                for (var actor : actors) actor.result();
            }
            List<String> admitted = new ArrayList<>();
            try (var statement = reader.createStatement(); var rows = statement.executeQuery("SELECT order_id FROM orders ORDER BY order_id")) {
                while (rows.next()) admitted.add(rows.getString(1));
            }
            reader.commit(); long admittedAt = elapsed(); evidence.admit(admitted, admittedAt);
            http(endpoint, "FILL");
            for (var actor : actors) actor.startCommand("L6_RECONCILE");
            for (var actor : actors) actor.result();
            var point = L6BusinessCheckpoint.verify(reader, endpoint, actors, dir, phase, ++checks, true);
            reader.commit(); long completedAt = elapsed();
            List<String> completed = new ArrayList<>(); point.path("fullChainOrderIds").forEach(id -> completed.add(id.asText()));
            evidence.complete(completed, completedAt);
            if (pressure) evidence.pause(pauseStart, completedAt, "BACKPRESSURE");
            else if (point.has("convergenceWaitStartedNanos")) evidence.pause(
                    millis(point.path("convergenceWaitStartedNanos").asLong() - started),
                    millis(point.path("convergenceWaitEndedNanos").asLong() - started), "BACKPRESSURE");
            point.put("phase", phase).put("admittedObservedElapsedMillis", admittedAt).put("completedObservedElapsedMillis", completedAt)
                    .put("producerBackpressured", pressure).put("sampledAt", Instant.now().toString());
            point.set("admittedOrderIds", JSON.valueToTree(admitted));
            Files.writeString(dir.resolve("progress.ndjson"), JSON.writeValueAsString(point) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            long wait = Math.min(started + endNanos, tick + TimeUnit.SECONDS.toNanos(5)) - System.nanoTime();
            if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
        }
        proof.withArray("phases").addObject().put("phase", phase).put("startElapsedMillis", phaseStart)
                .put("endElapsedMillis", elapsed()).put("actualDurationMillis", elapsed() - phaseStart);
    }

    private long elapsed() { return millis(System.nanoTime() - started); }
    private static long millis(long nanos) { return TimeUnit.NANOSECONDS.toMillis(nanos); }
}
