package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class L6CalibrationContractTest {
    @TempDir Path directory;

    @Test void controllableClockSeparatesFiveTenCalibrationFromTenFortyTenSoak() throws Exception {
        var cal = new L6CalibrationContract(false);
        var clock = new AtomicLong();
        var json = new ObjectMapper();
        Map<String, L6ResourceSampler.Collector> collectors = Map.of("clock", stamp -> L6RuntimeResources.measured(
                stamp, json.createObjectNode().put("value", stamp.elapsedMillis()), Set.of("value")));
        for (L6SamplingSchedule schedule : List.of(cal, L6DurationContract.forMode(false))) {
            clock.set(0);
            try (var sampler = new L6ResourceSampler(collectors, Map.of("clock", Set.of("value")), clock::get,
                    () -> Instant.EPOCH.plusNanos(clock.get()), 0, schedule, directory.resolve(schedule.getClass().getSimpleName()+".ndjson"))) {
                for (long t = 0; t < schedule.total(); t += TimeUnit.SECONDS.toNanos(10)) {
                    clock.set(t); sampler.sample();
                    assertEquals(schedule.samplePhase(t), sampler.latest().path("phase").asText());
                }
                sampler.requireComplete();
                assertEquals(schedule == cal ? 90 : 360, sampler.summary().path("sampleCount").asInt());
            }
        }
        assertEquals("WARMUP", cal.samplePhase(TimeUnit.SECONDS.toNanos(300)-1));
        assertEquals("MEASUREMENT", cal.samplePhase(TimeUnit.SECONDS.toNanos(300)));
        assertEquals("CLEANUP", cal.samplePhase(TimeUnit.SECONDS.toNanos(900)));
        assertEquals("ACTIVE", L6DurationContract.forMode(false).samplePhase(TimeUnit.SECONDS.toNanos(900)));
    }

    @Test void rateUsesFullChainObservationWindowAndSubtractsPauses() {
        var e = new L6CalibrationEvidence();
        e.admit(List.of("warm"), 299999); e.complete(List.of("warm"), 300001);
        e.admit(List.of("ok", "paused", "late"), 310000);
        e.complete(List.of("ok"), 320000); e.complete(List.of("paused"), 410000); e.complete(List.of("late"), 900000);
        e.pause(400000, 420000, "BACKPRESSURE");
        var result = e.export(new L6CalibrationContract(false), 900000, 0, 0);
        assertEquals(1, result.path("eligibleFullChainCompletions").asInt());
        assertEquals(580, result.path("producerActiveSeconds").asDouble());
        assertEquals(20, result.path("producerPausedSeconds").asDouble());
        assertTrue(result.path("rawRateEvidenceValid").asBoolean());
        assertEquals(1.0/580, result.path("healthyRateCandidate").asDouble());
        assertFalse(result.path("formalCalibrationAccepted").asBoolean());
        assertTrue(result.path("l6FinalArrivalRate").isNull());
    }

    @Test void incompleteChainsMissingSamplesPausedRunAndRunawayCannotProvideRate() {
        var e = new L6CalibrationEvidence(); e.admit(List.of("onlyOrder"), 310000);
        assertFalse(e.export(new L6CalibrationContract(false), 900000, 0, 0).path("rawRateEvidenceValid").asBoolean());
        e.complete(List.of("onlyOrder"), 320000);
        assertFalse(e.export(new L6CalibrationContract(false), 900000, 1, 0).path("rawRateEvidenceValid").asBoolean());
        assertFalse(e.export(new L6CalibrationContract(false), 900000, 0, 1).path("rawRateEvidenceValid").asBoolean());
        e.pause(400000, 750000, "BACKPRESSURE");
        assertTrue(e.export(new L6CalibrationContract(false), 900000, 0, 0).path("healthyRateCandidate").isNull());
        e.backlog(1); e.backlog(2);
        assertThrows(IllegalStateException.class, () -> e.backlog(3));
        assertThrows(IllegalStateException.class, () -> e.complete(List.of("unadmitted"), 330000));
    }

    @Test void smokeCannotPublishFormalRateOrAcceptance() {
        var e = new L6CalibrationEvidence(); e.admit(List.of("order"), 12000); e.complete(List.of("order"), 16000);
        var result = e.export(new L6CalibrationContract(true), 50000, 0, 0);
        assertEquals("CALIBRATION", result.path("mode").asText());
        assertTrue(result.path("rawRateEvidenceValid").asBoolean());
        assertTrue(result.path("healthyRateCandidate").isNull());
        assertFalse(result.path("formalCalibrationAccepted").asBoolean());
        assertFalse(result.path("l6Accepted").asBoolean());
    }

    @Test void analyzerAndDuplicateAccountingNegatives() throws Exception {
        String output = B0Processes.command("python", "-X", "utf8", B0Processes.root().resolve(
                "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_l6_calibration_analyzer.py").toString());
        assertTrue(output.contains("OK"), output);
    }
}
