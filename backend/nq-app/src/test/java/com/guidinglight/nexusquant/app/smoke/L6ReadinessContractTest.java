package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel.RuntimeEvidenceSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class L6ReadinessContractTest {
    @Test void missingMeasurementsNeverBecomeZeroDuringSerialization() throws Exception {
        var json = new ObjectMapper();
        var sample = json.readTree("""
                {"commandQueue":{"queuePresent":true,"queueSize":0,"activeCount":0,"completedCount":1,
                "queueCapacity":1,"measurementStatus":"MEASURED_ZERO"},"acquisitionTimeoutMetric":"hikaricp.connections.timeout",
                "acquisitionTimeoutCount":0,"counterStart":0,"counterEnd":0,"acquisitionTimeoutDelta":0,
                "candidateAge":{"sampledAt":"2026-09-13T00:00:00Z","scanEligibleCandidateCount":1,
                "eligibleCandidateCount":0,"oldestCandidateAgeMillis":null,"oldestAgeStatus":"NONE"}}
                """);
        L6Measurements.requireMandatory(json.readTree(sample.toString()));
        for (String field : List.of("commandQueue", "candidateAge", "acquisitionTimeoutMetric", "acquisitionTimeoutCount",
                "counterStart", "counterEnd", "acquisitionTimeoutDelta")) {
            var missing = (ObjectNode) sample.deepCopy();
            missing.remove(field);
            assertThrows(IllegalStateException.class, () -> L6Measurements.requireMandatory(missing));
        }
        var absentAge = sample.deepCopy();
        ((ObjectNode) absentAge.path("candidateAge")).remove("oldestCandidateAgeMillis");
        assertThrows(IllegalStateException.class, () -> L6Measurements.requireMandatory(absentAge));
        var unavailableQueue = sample.deepCopy();
        ((ObjectNode) unavailableQueue.path("commandQueue")).put("measurementStatus", "UNAVAILABLE");
        assertThrows(IllegalStateException.class, () -> L6Measurements.requireMandatory(unavailableQueue));
    }

    @Test void formalBoundariesIgnoreOrdinaryDurationProperty() {
        String old = System.getProperty("duration");
        try {
            System.setProperty("duration", "1");
            var c = L6DurationContract.forMode(false);
            long minute = Duration.ofMinutes(1).toNanos();
            assertEquals(L6DurationContract.Phase.WARMUP, c.phase(10 * minute - 1));
            assertEquals(L6DurationContract.Phase.ACTIVE, c.phase(10 * minute));
            assertEquals(L6DurationContract.Phase.ACTIVE, c.phase(50 * minute - 1));
            assertEquals(L6DurationContract.Phase.DRAIN, c.phase(50 * minute));
            assertEquals(L6DurationContract.Phase.DRAIN, c.phase(60 * minute - 1));
            assertEquals(L6DurationContract.Phase.COMPLETE, c.phase(60 * minute));
            assertEquals(60 * minute, c.total());
            assertEquals(Duration.ofSeconds(115).toNanos(), L6DurationContract.forMode(true).total());
        } finally {
            if (old == null) System.clearProperty("duration"); else System.setProperty("duration", old);
        }
    }

    @Test void expectedGapDoesNotPermitOtherDegradationOrUnknownSource() throws Exception {
        var sources = sources();
        var result = L6ValidationContract.evaluate(overview(sources), 1, 1, 0);
        assertEquals("VALIDATION_AGGREGATE_DEGRADED_EXPECTED", result.path("sourceAvailabilityStatus").asText());
        assertEquals(result.toString(), new ObjectMapper().readTree(result.toString()).toString());
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).sourceKey().equals(L6ValidationContract.GAP)) continue;
            for (Availability unavailable : List.of(Availability.UNAVAILABLE, Availability.UNKNOWN, Availability.PARTIAL)) {
                var bad = new ArrayList<>(sources);
                bad.set(i, new RuntimeEvidenceSource(sources.get(i).sourceKey(), "fixture", metadata("fixture", unavailable, FreshnessStatus.UNKNOWN)));
                assertThrows(IllegalStateException.class, () -> L6ValidationContract.evaluate(overview(bad), 1, 1, 0));
            }
            var stale = new ArrayList<>(sources);
            stale.set(i, new RuntimeEvidenceSource(sources.get(i).sourceKey(), "fixture", metadata("fixture", Availability.AVAILABLE, FreshnessStatus.STALE)));
            assertThrows(IllegalStateException.class, () -> L6ValidationContract.evaluate(overview(stale), 1, 1, 0));
            var unknown = new ArrayList<>(sources);
            unknown.set(i, new RuntimeEvidenceSource(sources.get(i).sourceKey(), "fixture", metadata("fixture", Availability.AVAILABLE, FreshnessStatus.UNKNOWN)));
            var rejected = assertThrows(IllegalStateException.class, () -> L6ValidationContract.evaluate(overview(unknown), 1, 1, 0));
            assertEquals("VALIDATION_UNEXPECTED_DEGRADATION", rejected.getMessage());
        }
        var extra = new ArrayList<>(sources); extra.add(sources.getFirst());
        assertThrows(IllegalStateException.class, () -> L6ValidationContract.evaluate(overview(extra), 1, 1, 0));
        var missing = new ArrayList<>(sources); missing.removeFirst();
        assertThrows(IllegalStateException.class, () -> L6ValidationContract.evaluate(overview(missing), 1, 1, 0));
        var wrongGap = new ArrayList<>(sources);
        wrongGap.removeIf(s -> s.sourceKey().equals(L6ValidationContract.GAP));
        wrongGap.add(new RuntimeEvidenceSource("OTHER_NO_FILE", "fixture", metadata("fixture", Availability.UNAVAILABLE, FreshnessStatus.UNKNOWN)));
        assertThrows(IllegalStateException.class, () -> L6ValidationContract.evaluate(overview(wrongGap), 1, 1, 0));
    }

    @Test void missingCompletionAndSchedulerFailureNeverPass() {
        assertThrows(IllegalStateException.class, () -> L6ValidationContract.evaluate(overview(sources()), 1, 0, 0));
        var failure = assertThrows(IllegalStateException.class, () -> L6ValidationContract.evaluate(overview(sources()), 1, 1, 1));
        assertEquals("VALIDATION_SCHEDULER_EXECUTION_FAILED", failure.getMessage());
    }

    @Test void executorMetricsFollowRealWaitingAndCompletion() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        try (var executor = L6Measurements.commands()) {
            assertEquals("MEASURED_ZERO", L6Measurements.queue(executor).path("measurementStatus").asText());
            var first = executor.submit(() -> { entered.countDown(); release.await(); return true; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> true);
            try {
                assertEquals(1, L6Measurements.queue(executor).path("queueSize").asInt());
                assertEquals(1, L6Measurements.queue(executor).path("activeCount").asInt());
                assertEquals(1, L6Measurements.queue(executor).path("queueCapacity").asInt());
                assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> true));
            } finally { release.countDown(); }
            assertTrue(first.get(5, TimeUnit.SECONDS)); assertTrue(second.get(5, TimeUnit.SECONDS));
            executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(2, L6Measurements.queue(executor).path("completedCount").asLong());
            assertEquals(0, L6Measurements.queue(executor).path("queueSize").asInt());
        } finally { release.countDown(); }
        assertThrows(IllegalStateException.class, () -> L6Measurements.queue(null));
    }

    private static List<RuntimeEvidenceSource> sources() {
        return L6ValidationContract.SOURCES.stream().map(key -> new RuntimeEvidenceSource(key, key,
                key.equals(L6ValidationContract.GAP) ? metadata("LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW", Availability.UNAVAILABLE, FreshnessStatus.UNKNOWN)
                        : metadata("fixture", Availability.AVAILABLE, FreshnessStatus.FRESH))).toList();
    }

    private static ReadModelEvidenceMetadata metadata(String source, Availability availability, FreshnessStatus freshness) {
        return new ReadModelEvidenceMetadata(source, availability, availability == Availability.AVAILABLE ? Instant.now() : null,
                freshness, availability == Availability.AVAILABLE ? 0L : null, null, null, true, true, true, true);
    }

    private static ValidationOperationsRuntimeEvidenceOverviewReadModel overview(List<RuntimeEvidenceSource> sources) {
        return new ValidationOperationsRuntimeEvidenceOverviewReadModel(Instant.now(), metadata("aggregate", Availability.PARTIAL, FreshnessStatus.UNKNOWN),
                sources.size(), 4, 0, 1, 0, 4, 0, 1, sources, "l6-contract");
    }
}
