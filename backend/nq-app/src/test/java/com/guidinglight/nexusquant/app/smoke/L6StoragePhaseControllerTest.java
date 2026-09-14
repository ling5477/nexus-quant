package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** 可控时钟与真实互斥阻塞分别证明deadline、admission和采集执行通道的隔离。 */
class L6StoragePhaseControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long SECOND = 1_000_000_000L;
    private static final long INTERVAL = 7_117_650_000L;
    private final L6DurationContract duration = new L6StorageCalibrationContract(false).timing();
    private final AtomicLong clock = new AtomicLong();
    @TempDir Path directory;

    private L6ResourceSampler sampler(String name, L6ResourceSampler.Collector collector) throws Exception {
        var folder = Files.createDirectories(directory.resolve(name));
        return new L6ResourceSampler(Map.of("resource", collector), Map.of("resource", Set.of("value")),
                clock::get, () -> Instant.EPOCH.plusNanos(clock.get()), 0, duration, folder.resolve("resources.ndjson"));
    }

    private L6ResourceSampler.Observation measured(L6ResourceSampler.Stamp stamp) {
        return L6RuntimeResources.measured(stamp, JSON.createObjectNode().put("value", stamp.sampleIndex()), Set.of("value"));
    }

    private L6StoragePhaseController controller(L6StoragePhaseController.Observer observer) {
        return new L6StoragePhaseController(duration, clock::get, 0, observer, directory.resolve("phase.json"), Runnable::run);
    }

    @Test void exactDeadlinesTriggerFiveUniqueBoundariesInOrderWithSeparateSampleIdentities() throws Exception {
        try (var periodic = sampler("periodic", this::measured);
             var boundaries = sampler("boundary", this::measured);
             var phase = controller(boundaries::boundaryAt)) {
            periodic.sample();
            clock.set(300 * SECOND - 1); assertEquals("WARMUP", phase.phase());
            clock.incrementAndGet(); assertEquals("MEASUREMENT", phase.phase()); phase.advance(); phase.advance();
            phase.admit(0, () -> { });
            clock.set(1500 * SECOND - 1); assertTrue(phase.producerAllowed());
            clock.incrementAndGet(); assertEquals("DRAIN", phase.phase()); assertFalse(phase.producerAllowed()); phase.advance();
            assertTrue(phase.drainReady());
            clock.set(2100 * SECOND); assertEquals("COMPLETE", phase.phase()); phase.advance(); phase.advance(); phase.awaitEnd();
            var samples = boundaries.summary().path("samples");
            var expected = List.of("WARMUP_END", "MEASUREMENT_START", "MEASUREMENT_END", "DRAIN_START", "DRAIN_END");
            var actual = new ArrayList<String>(); var tokens = new ArrayList<String>();
            for (var sample : samples) {
                actual.add(sample.path("boundaryType").asText()); tokens.add(sample.path("sampleToken").asText());
                assertEquals("PHASE_BOUNDARY", sample.path("sampleType").asText());
                assertEquals(sample.path("scheduledElapsedNanos"), sample.path("observedElapsedNanos"));
                assertEquals(0, sample.path("boundaryObservationLatenessMillis").asDouble());
                assertTrue(sample.hasNonNull("phaseBefore") && sample.hasNonNull("phaseAfter"));
            }
            assertEquals(expected, actual); assertEquals(5, Set.copyOf(tokens).size());
            assertEquals(List.of("WARMUP", "MEASUREMENT", "MEASUREMENT", "DRAIN", "DRAIN"),
                    StreamSupport.stream(samples.spliterator(), false).map(s -> s.path("phase").asText()).toList());
            assertEquals(1, periodic.summary().path("sampleCount").asInt());
            assertEquals(0, boundaries.summary().path("sampleCount").asInt());
            assertEquals(5, boundaries.summary().path("boundarySampleCount").asInt());
            assertFalse(tokens.contains(periodic.summary().path("samples").get(0).path("sampleToken").asText()));
            var events = new ArrayList<String>(); phase.summary().path("events").forEach(e -> events.add(e.path("type").asText()));
            assertTrue(events.indexOf("PRODUCER_ADMISSION_CLOSED") < events.indexOf("MEASUREMENT_WORKLOAD_FROZEN"));
            assertTrue(events.indexOf("MEASUREMENT_WORKLOAD_FROZEN") < events.indexOf("MEASUREMENT_END"));
            assertTrue(events.indexOf("MEASUREMENT_END") < events.indexOf("DRAIN_ENTERED"));
            assertTrue(events.indexOf("DRAIN_ENTERED") < events.indexOf("DRAIN_START"));
        }
    }

    @Test void periodicStartLateBeyond3178MillisRejectsOnlyItsOwnCadenceAndCannotDelayCutoff() throws Exception {
        var periodic = sampler("late-periodic", this::measured);
        try (var phase = controller(b -> { })) {
            for (int i = 0; i < 150; i++) {
                clock.set(i * 10 * SECOND); periodic.sample(); if (i == 30) phase.advance();
            }
            clock.set(1500 * SECOND); phase.advance();
            clock.addAndGet(4 * SECOND);
            assertThrows(IllegalStateException.class, periodic::sample);
            assertEquals("DRAIN", phase.phase()); assertFalse(phase.producerAllowed()); assertDoesNotThrow(phase::checkHealthy);
            assertThrows(IllegalStateException.class, () -> phase.admit(1, () -> fail("late admission")));
            assertEquals(4000, periodic.summary().path("samples").get(150).path("periodicSamplerLatenessMillis").asLong());
        } finally { assertThrows(IllegalStateException.class, periodic::close); }
    }

    @Test void blockedPeriodicCollectorCannotHoldPhaseOrBoundaryCollectorLock() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        L6ResourceSampler.Collector collector = stamp -> {
            if (stamp.sampleIndex() == 150) {
                entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout");
            }
            return measured(stamp);
        };
        try (var periodic = sampler("blocked-periodic", collector); var boundaries = sampler("independent-boundary", collector);
             var phase = controller(boundaries::boundaryAt); var executor = Executors.newSingleThreadExecutor()) {
            for (int i = 0; i < 150; i++) {
                clock.set(i * 10 * SECOND); periodic.sample(); if (i == 30) phase.advance();
            }
            clock.set(1500 * SECOND); var pending = executor.submit(periodic::sample);
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS)); phase.advance();
                assertEquals(4, boundaries.summary().path("boundarySampleCount").asInt());
                assertFalse(phase.producerAllowed()); assertTrue(phase.drainReady());
                clock.addAndGet(4 * SECOND);
            } finally { release.countDown(); }
            pending.get(2, TimeUnit.SECONDS);
            assertEquals(4000, periodic.latest().path("collectionMillis").asLong());
        }
    }

    @Test void blockedCheckpointAndMissedSlotsNeverProduceAfterMeasurementDeadline() throws Exception {
        var pending = new ArrayDeque<Runnable>(); var observed = new ArrayList<String>();
        try (var phase = new L6StoragePhaseController(duration, clock::get, 0, b -> observed.add(b.type()),
                directory.resolve("queued.json"), pending::add)) {
            clock.set(300 * SECOND); phase.advance(); pending.remove().run();
            var pacer = new L6DeterministicPacer(clock::get, 0, INTERVAL, duration, phase::producerAllowed);
            var records = new ArrayList<L6DeterministicPacer.Slot>(); var emitted = new ArrayList<Long>();
            clock.set(duration.activeEnd() - 1);
            pacer.poll(false, slot -> { phase.admit(slot, () -> emitted.add(clock.get())); return "before"; }, records::add);
            assertEquals(List.of(duration.activeEnd() - 1), emitted);
            // 检查点仍在执行且boundary snapshot尚未派发，不影响时间本身关闭admission。
            clock.set(duration.activeEnd());
            assertFalse(phase.producerAllowed());
            assertThrows(IllegalStateException.class, () -> phase.admit(212, () -> fail("at deadline")));
            phase.advance(); assertFalse(phase.drainReady()); assertEquals(1, pending.size());
            clock.addAndGet(5 * SECOND);
            pacer.poll(false, slot -> fail("missed workload replayed"), records::add);
            assertThrows(IllegalStateException.class, () -> phase.admit(213, () -> fail("after deadline")));
            assertEquals(1, emitted.size());
        }
    }

    @Test void delayedControllerRejectsWithoutExtendingLogicalPhaseOrProducerGrace() throws Exception {
        var phase = controller(b -> { });
        clock.set(300 * SECOND); phase.advance();
        clock.set(1500 * SECOND + 3_178_000_000L);
        assertEquals("DRAIN", phase.phase()); assertFalse(phase.producerAllowed());
        assertEquals("PHASE_TRANSITION_DEADLINE_VIOLATION", assertThrows(IllegalStateException.class, phase::advance).getMessage());
        assertThrows(IllegalStateException.class, phase::close);
    }

    @Test void timelyControllerWithLateBoundaryCollectorHasDistinctFailClosedReason() throws Exception {
        var queued = new ArrayDeque<Runnable>();
        var sampler = sampler("late-boundary", this::measured);
        var phase = new L6StoragePhaseController(duration, clock::get, 0, sampler::boundaryAt,
                directory.resolve("late-controller.json"), queued::add);
        clock.set(300 * SECOND); phase.advance(); clock.addAndGet(3_178_000_000L); queued.remove().run();
        assertThrows(IllegalStateException.class, phase::checkHealthy);
        assertEquals(0, phase.summary().path("events").get(0).path("phaseTransitionLatenessMillis").asLong());
        assertTrue(sampler.summary().path("samples").get(0).path("error").asText().contains("BOUNDARY_OBSERVATION_DEADLINE_VIOLATION"));
        assertThrows(IllegalStateException.class, phase::close); assertThrows(IllegalStateException.class, sampler::close);
    }

    @Test void slowBoundaryCollectionRetainsEightSecondCeiling() throws Exception {
        var sampler = sampler("slow-boundary", stamp -> { clock.addAndGet(9 * SECOND); return measured(stamp); });
        clock.set(300 * SECOND);
        assertThrows(IllegalStateException.class, () -> sampler.boundaryAt(new L6StoragePhaseController.Boundary(
                "WARMUP_END", 300 * SECOND, "WARMUP", "MEASUREMENT")));
        assertTrue(sampler.summary().path("samples").get(0).path("error").asText().contains("BOUNDARY_OBSERVATION_DEADLINE_VIOLATION"));
        assertThrows(IllegalStateException.class, sampler::close);
    }

    @Test void capacityStopKeepsBoundedDrainWithoutDispatchingLaterFormalBoundaries() throws Exception {
        var names = new ArrayList<String>();
        try (var phase = controller(b -> names.add(b.type()))) {
            clock.set(10 * SECOND); phase.capacityStop();
            assertEquals("DRAIN", phase.phase()); assertFalse(phase.producerAllowed());
            assertTrue(phase.drainReady());
            clock.set(30 * SECOND); phase.endCapacityDrain(); phase.awaitEnd();
            clock.set(300 * SECOND); phase.advance();
            assertEquals(List.of("CAPACITY_RISK_STOP", "DRAIN_START", "DRAIN_END"), names);
        }
    }

    @Test void everyBusinessDispatchRechecksDeadlineAfterMidIterationDelay() throws Exception {
        var queued = new ArrayDeque<Runnable>();
        try (var phase = new L6StoragePhaseController(duration, clock::get, 0, b -> { },
                directory.resolve("dispatch-gate.json"), queued::add)) {
            clock.set(300 * SECOND); phase.advance(); queued.remove().run();
            clock.set(1500 * SECOND - 1); assertDoesNotThrow(phase::requireBusinessDispatchReady);
            // 顶部检查通过后，模拟DB读取/前一命令结果等待在同轮中跨过deadline。
            clock.incrementAndGet();
            for (String operation : List.of("L6_OBSERVER_SCAN", "FILL", "L6_RECONCILE", "CHECKPOINT")) {
                assertThrows(L6StoragePhaseController.BoundaryPending.class, phase::requireBusinessDispatchReady, operation);
            }
            phase.advance();
            assertThrows(L6StoragePhaseController.BoundaryPending.class, phase::requireBusinessDispatchReady);
            queued.remove().run(); assertDoesNotThrow(phase::requireBusinessDispatchReady);
        }
    }

    @Test void machineResultDistinguishesControllerAndBoundaryTimingFailures() {
        for (String reason : List.of("PHASE_TRANSITION_DEADLINE_VIOLATION", "BOUNDARY_OBSERVATION_DEADLINE_VIOLATION")) {
            var error = new IllegalStateException("outer evidence failure", new IllegalStateException(reason));
            assertEquals("BLOCKED / " + reason, L6StoragePhaseController.timingFailureResult(error));
        }
        assertEquals("FAILED", L6StoragePhaseController.timingFailureResult(new IllegalStateException("ordinary failure")));
    }
}
