package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class L6StorageContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    @Test void explicitTimingAndFrozenPacerAreSeparate() throws Exception {
        var storage = new L6StorageCalibrationContract(false).timing();
        assertEquals(300_000_000_000L, storage.warmupNanos());
        assertEquals(1_200_000_000_000L, storage.activeNanos());
        assertEquals(600_000_000_000L, storage.drainNanos());
        assertEquals(2_100_000_000_000L, storage.total());
        assertEquals(3_600_000_000_000L, L6FormalManifest.timing(false).total());
        var manifest = L6FormalManifest.read(B0Processes.root().resolve(L6FormalManifest.CANONICAL));
        assertEquals(7_117_650_000L, manifest.intervalNanos());
        var clock = new AtomicLong(); var sent = new ArrayList<Long>(); var slots = new ArrayList<L6DeterministicPacer.Slot>();
        var pacer = new L6DeterministicPacer(clock::get, 0, manifest.intervalNanos(), storage);
        pacer.poll(false, n -> { sent.add(clock.get()); return "order-" + n; }, slots::add);
        clock.set(manifest.intervalNanos() * 4);
        pacer.poll(false, n -> { sent.add(clock.get()); return "order-" + n; }, slots::add);
        assertEquals(2, sent.size());
        clock.set(storage.activeEnd());
        pacer.poll(false, n -> fail("drain emitted"), slots::add);
        assertEquals(2, sent.size()); manifest.verifyUnchanged();
    }

    private ObjectNode storage(long used, long at) {
        var record = JSON.createObjectNode().put("elapsedMillis", at);
        var v = record.putObject("sources").putObject("postgres").putObject("values");
        v.put("pgTmpfsUsedBytes", used).put("pgTmpfsFreeBytes", 268435456L - used);
        return record;
    }

    @Test void riskIsLatchedAndNeverProducesAfterStopEvenWhenSpaceReturns() throws Exception {
        var guard = new L6StorageCapacityGuard(); var clock = new AtomicLong();
        var emitted = new ArrayList<Long>(); var slots = new ArrayList<L6DeterministicPacer.Slot>();
        var pacer = new L6DeterministicPacer(clock::get, 0, 7_117_650_000L, new L6StorageCalibrationContract(false).timing());
        guard.observe(storage(40_000_000, 0), 0);
        pacer.poll(false, n -> { guard.produce(() -> emitted.add(n)); return "o" + n; }, slots::add);
        assertEquals(1, emitted.size()); assertFalse(guard.stopped());
        clock.set(10_000_000_000L); guard.observe(storage(240_000_000, 10_000), clock.get());
        assertEquals("DRAIN", guard.phase("MEASUREMENT"));
        assertEquals("BLOCKED / STORAGE_CALIBRATION_CAPACITY_AT_RISK", guard.result());
        assertThrows(L6StorageCapacityGuard.ProducerStopped.class, () ->
                pacer.poll(false, n -> { guard.produce(() -> emitted.add(n)); return "o" + n; }, slots::add));
        guard.observe(storage(40_000_000, 20_000), 20_000_000_000L);
        assertThrows(L6StorageCapacityGuard.ProducerStopped.class, () -> guard.produce(() -> emitted.add(999L)));
        assertEquals(1, emitted.size()); assertTrue(guard.drainExpired(30_000_000_000L));
    }

    @Test void boundariesUseSameCollectorButNeverIncreasePeriodicCount() throws Exception {
        var clock = new AtomicLong(); var calls = new AtomicLong();
        var fields = Set.of("TradeExecuted", "fullChainCompleted", "pgTmpfsCapacityBytes", "pgTmpfsUsedBytes", "pgTmpfsFreeBytes", "databaseBytes", "walBytes");
        var values = JSON.createObjectNode(); fields.forEach(f -> values.put(f, 1));
        try (var sampler = new L6ResourceSampler(Map.of("postgres", stamp -> {
            calls.incrementAndGet(); return L6RuntimeResources.measured(stamp, values.deepCopy(), fields);
        }), Map.of("postgres", fields), clock::get, () -> Instant.EPOCH, 0,
                new L6StorageCalibrationContract(true).timing(), directory.resolve("samples.ndjson"))) {
            sampler.sample();
            for (String boundary : new String[]{"WARMUP_END", "MEASUREMENT_START", "MEASUREMENT_END", "DRAIN_START", "DRAIN_END"}) {
                var row = sampler.boundary(boundary, "DRAIN");
                assertEquals("PHASE_BOUNDARY", row.path("sampleType").asText());
            }
            assertEquals(1, sampler.summary().path("sampleCount").asInt());
            assertEquals(5, sampler.summary().path("boundarySampleCount").asInt());
            assertEquals(6, calls.get());
        }
        for (String missing : fields) {
            var bad = values.deepCopy(); bad.remove(missing);
            var sampler = new L6ResourceSampler(Map.of("postgres", stamp -> L6RuntimeResources.measured(stamp, bad, fields)),
                    Map.of("postgres", fields), clock::get, () -> Instant.EPOCH, 0,
                    new L6StorageCalibrationContract(true).timing(), directory.resolve(missing + ".ndjson"));
            assertThrows(IllegalStateException.class, sampler::sample);
            assertThrows(IllegalStateException.class, sampler::close);
        }
    }

    @Test void lateBoundaryRejectsInsteadOfPretendingExactObservation() {
        var clock = new AtomicLong(23_000_000_000L);
        var fields = Set.of("fullChainCompleted");
        var sampler = new L6ResourceSampler(Map.of("postgres", stamp -> L6RuntimeResources.measured(stamp,
                JSON.createObjectNode().put("fullChainCompleted", 0), fields)), Map.of("postgres", fields),
                clock::get, () -> Instant.EPOCH, 0, new L6StorageCalibrationContract(true).timing(), directory.resolve("late.ndjson"));
        assertThrows(IllegalStateException.class, () -> sampler.boundaryAt("WARMUP_END/MEASUREMENT_START", "MEASUREMENT", 20_000_000_000L));
        assertEquals(0, sampler.summary().path("sampleCount").asInt());
        assertThrows(IllegalStateException.class, sampler::close);
    }

    @Test void healthyTrajectoryKeepsProductionOpenAndRiskDrainIsBounded() {
        var guard = new L6StorageCapacityGuard();
        for (int i = 0; i < 10; i++) {
            guard.observe(storage(40_000_000 + i * 100_000, i * 10_000), i * 10_000_000_000L);
            assertTrue(guard.producerAllowed());
        }
        guard.observe(storage(240_000_000, 100_000), 100_000_000_000L);
        assertEquals("DRAIN", guard.phase("WARMUP"));
        assertTrue(guard.drainExpired(120_000_000_000L));
        assertEquals("BLOCKED / STORAGE_CALIBRATION_CAPACITY_AT_RISK", guard.result());
    }

    @Test void periodicAndBoundaryTokensTraverseActualLoopbackEndpoint() throws Exception {
        var clock = new AtomicLong(); var fields = Set.of("fullChainCompleted");
        try (var endpoint = new L6MetricsEndpoint(() -> JSON.createObjectNode().put("fullChainCompleted", 1));
             var client = java.net.http.HttpClient.newHttpClient();
             var sampler = new L6ResourceSampler(Map.of("postgres", stamp -> {
                 var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint.endpoint() + "/metrics"))
                         .timeout(java.time.Duration.ofSeconds(2)).header("X-L6-Sample", stamp.token()).GET().build();
                 var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                 assertEquals(200, response.statusCode());
                 var body = (ObjectNode) JSON.readTree(response.body());
                 assertEquals(stamp.token(), body.path("sampleToken").asText());
                 return L6RuntimeResources.measured(stamp, body, fields);
             }), Map.of("postgres", fields), clock::get, Instant::now, 0,
                     new L6StorageCalibrationContract(true).timing(), directory.resolve("wire.ndjson"))) {
            sampler.sample();
            var boundary = sampler.boundary("WARMUP_END/MEASUREMENT_START", "MEASUREMENT");
            assertNotEquals(sampler.summary().path("samples").get(0).path("sampleToken"), boundary.path("sampleToken"));
            assertEquals(1, sampler.summary().path("sampleCount").asInt());
            assertEquals(1, sampler.summary().path("boundarySampleCount").asInt());
        }
    }

    @Test void failedCollectorRemainsPrimaryDuringResourceClose() {
        var fields = Set.of("fullChainCompleted");
        var failure = assertThrows(IllegalStateException.class, () -> {
            try (var sampler = new L6ResourceSampler(Map.of("postgres", stamp -> {
                throw new IllegalStateException("original collector failure");
            }), Map.of("postgres", fields), () -> 0L, Instant::now, 0,
                    new L6StorageCalibrationContract(true).timing(), directory.resolve("failure.ndjson"))) {
                sampler.sample();
            }
        });
        assertEquals("L6_RESOURCE_QUALIFICATION_REJECTED", failure.getMessage());
        assertEquals("original collector failure", failure.getCause().getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("L6_SAMPLER_CLOSED_WITH_FAILURE", failure.getSuppressed()[0].getMessage());
    }
}
