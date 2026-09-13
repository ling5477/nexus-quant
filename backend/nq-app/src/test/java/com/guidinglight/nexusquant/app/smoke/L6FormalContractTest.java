package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class L6FormalContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long INTERVAL = 7_117_650_000L;
    @TempDir Path directory;

    private Path canonical() { return B0Processes.root().resolve(L6FormalManifest.CANONICAL); }
    @Test void acceptedReadOnlyManifestAndDerivedCapacity() throws Exception {
        byte[] before = Files.readAllBytes(canonical());
        var manifest = L6FormalManifest.read(canonical());
        assertEquals(INTERVAL, manifest.intervalNanos());
        assertEquals(0.14049580971247533, manifest.frozen().path("finalL6ArrivalRate").asDouble());
        assertEquals(422, manifest.capacity(L6DurationContract.forMode(false)).runOrderBudget());
        assertEquals(422, manifest.capacity(L6DurationContract.forMode(false)).venueLogicalOrderCapacity());
        assertEquals(3000, manifest.capacity(L6DurationContract.forMode(false)).globalStageSafetyCap());
        assertEquals(300, QualificationCapacity.l5().runOrderBudget());
        assertEquals(600, L6CalibrationBudget.frozen().runOrderBudget());
        manifest.verifyUnchanged(); assertArrayEquals(before, Files.readAllBytes(canonical()));
    }

    @Test void invalidManifestCannotReachRuntimeOrTimer() throws Exception {
        List<Consumer<ObjectNode>> mutations = List.of(
            n -> n.put("schemaVersion", 2), n -> n.put("formalCalibrationAccepted", false),
            n -> n.put("status", "PENDING"), n -> n.put("decision", "PASS"),
            n -> n.remove("finalL6ArrivalRate"), n -> n.put("finalL6ArrivalRate", 0),
            n -> n.put("finalL6ArrivalRate", -1), n -> n.put("finalL6ArrivalRate", 1.1),
            n -> n.put("pacingInterval", 0), n -> n.put("pacingIntervalNanos", 1),
            n -> n.remove("healthySustainedRate"), n -> n.remove("sourceHead"),
            n -> n.remove("exactHeadCi"), n -> n.remove("noiseBands"),
            n -> n.withObject("/sampling").put("intervalMillis", 5000),
            n -> n.withObject("/sampling").put("staleReuse", 1),
            n -> n.withObject("/correctness").put("P1", 1),
            n -> n.withObject("/correctness").put("ledgerBalanced", false),
            n -> n.withObject("/noiseBands").putArray("heap"),
            n -> n.withObject("/noiseBands").putArray("resources"));
        var starts = new AtomicInteger();
        int i = 0;
        for (var mutation : mutations) {
            ObjectNode n = (ObjectNode) JSON.readTree(Files.readAllBytes(canonical())); mutation.accept(n);
            Path path = directory.resolve("invalid-" + i++ + ".json"); Files.writeString(path, n.toString());
            var failure = assertThrows(IllegalStateException.class, () -> L6FormalManifest.start(path,
                    L6DurationContract.forMode(false), (m,c) -> starts.incrementAndGet()));
            assertEquals(L6FormalManifest.INVALID, failure.getMessage());
        }
        for (String content : List.of("{broken", "null", "[]", "{\"schemaVersion\":1,\"schemaVersion\":1}", Files.readString(canonical()) + " {}")) {
            Path path = directory.resolve("malformed.json"); Files.writeString(path, content);
            assertThrows(IllegalStateException.class, () -> L6FormalManifest.start(path,
                    L6DurationContract.forMode(false), (m,c) -> starts.incrementAndGet()));
        }
        assertThrows(IllegalStateException.class, () -> L6FormalManifest.start(directory.resolve("missing"),
                L6DurationContract.forMode(false), (m,c) -> starts.incrementAndGet()));
        assertEquals(0, starts.get());
    }

    @Test void semanticIdentityIgnoresFormattingButExitIdentityDoesNot() throws Exception {
        Path path = directory.resolve("input.json"); Files.copy(canonical(), path);
        var first = L6FormalManifest.read(path);
        Files.writeString(path, JSON.readTree(Files.readAllBytes(path)).toString());
        var second = L6FormalManifest.read(path);
        assertEquals(first.identity().path("semanticFingerprint"), second.identity().path("semanticFingerprint"));
        assertNotEquals(first.identity().path("sha256"), second.identity().path("sha256"));
        assertThrows(IllegalStateException.class, first::verifyUnchanged);
    }

    @Test void capacityRejectsBeforeTimerAndDoesNotBorrowL5Limit() {
        var manifest = L6FormalManifest.read(canonical());
        var starts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> L6FormalManifest.start(canonical(),
                new L6DurationContract(INTERVAL * 3000, INTERVAL, INTERVAL), (m,c) -> starts.incrementAndGet()));
        assertEquals(0, starts.get());
        var capacity = manifest.capacity(L6DurationContract.forMode(false));
        capacity.reserve(421, 1); assertThrows(IllegalStateException.class, () -> capacity.reserve(422, 1));
        assertThrows(IllegalStateException.class, () -> new QualificationCapacity(QualificationCapacity.Mode.L6_FORMAL, 422, 422, 421).validate());
    }

    @Test void absoluteSlotsNeverAccumulateSleepDriftOrSchedulerCadence() throws Exception {
        for (long observerCadence : List.of(1_000_000_000L, 5_000_000_000L, 60_000_000_000L)) {
            var clock = new AtomicLong(123);
            var pacer = new L6DeterministicPacer(clock::get, 123, INTERVAL, L6DurationContract.forMode(false));
            var records = new ArrayList<L6DeterministicPacer.Slot>();
            for (int i = 0; i < 100; i++) {
                // 模拟不同扫描节奏的唤醒；到达权只来自slot deadline，observer本身不emit。
                long due = 123 + i * INTERVAL;
                long observer = due - observerCadence;
                if (observer >= clock.get()) { clock.set(observer); pacer.poll(false, slot -> "order-" + slot, records::add); }
                clock.set(due + 1000);
                pacer.poll(false, slot -> "order-" + slot, records::add);
            }
            var emitted = records.stream().filter(s -> s.decision().equals("EMITTED")).toList();
            assertEquals(100, emitted.size());
            for (int i = 0; i < 100; i++) assertEquals(i * INTERVAL, emitted.get(i).scheduledElapsed());
            assertEquals(INTERVAL, emitted.get(1).scheduledElapsed()); assertEquals(14_235_300_000L, emitted.get(2).scheduledElapsed());
        }
    }

    @Test void backpressureAndLateWakeNeverAccumulateDebt() throws Exception {
        var clock = new AtomicLong(); var pacer = new L6DeterministicPacer(clock::get, 0, INTERVAL, L6DurationContract.forMode(false));
        var records = new ArrayList<L6DeterministicPacer.Slot>(); var count = new AtomicInteger();
        for (int i = 0; i < 3; i++) { clock.set(i * INTERVAL); pacer.poll(true, slot -> "unexpected", records::add); }
        clock.set(3 * INTERVAL); pacer.poll(false, slot -> "order-" + count.incrementAndGet(), records::add);
        assertEquals(1, count.get()); assertEquals(3, records.stream().filter(s -> s.decision().equals("PAUSED_BACKPRESSURE")).count());
        clock.set(7 * INTERVAL + 500); pacer.poll(false, slot -> "order-" + count.incrementAndGet(), records::add);
        pacer.poll(false, slot -> "order-" + count.incrementAndGet(), records::add);
        assertEquals(2, count.get()); assertEquals(7, records.getLast().slotIndex());
    }

    @Test void delayedEmissionCannotCompressActualIntervalsAndDrainNeverEmits() throws Exception {
        var clock = new AtomicLong();
        var duration = new L6DurationContract(2 * INTERVAL, 2 * INTERVAL, 2 * INTERVAL);
        var pacer = new L6DeterministicPacer(clock::get, 0, INTERVAL, duration);
        var records = new ArrayList<L6DeterministicPacer.Slot>();
        clock.set(1000);
        pacer.poll(false, slot -> { clock.addAndGet(1000); return "order-0"; }, records::add);
        clock.set(INTERVAL); pacer.poll(false, slot -> fail("compressed interval"), records::add);
        clock.set(INTERVAL + 1000); pacer.poll(false, slot -> fail("missed debt"), records::add);
        clock.set(2 * INTERVAL); pacer.poll(false, slot -> "active-order", records::add);
        clock.set(4 * INTERVAL); pacer.poll(false, slot -> fail("drain emitted"), records::add);
        clock.set(5 * INTERVAL); pacer.poll(false, slot -> fail("drain emitted"), records::add);
        assertEquals(2, records.stream().filter(s -> s.decision().equals("EMITTED")).count());
        assertTrue(records.stream().anyMatch(s -> s.decision().equals("SKIPPED_PHASE")));
    }

    @Test void repeatedServiceCostDoesNotShiftAnyAbsoluteDeadline() throws Exception {
        var clock = new AtomicLong(); var pacer = new L6DeterministicPacer(clock::get, 0, INTERVAL, L6DurationContract.forMode(false));
        var records = new ArrayList<L6DeterministicPacer.Slot>();
        for (int i = 0; i < 100; i++) {
            clock.set(i * INTERVAL);
            pacer.poll(false, slot -> { clock.addAndGet(100_000_000); return "order-" + slot; }, records::add);
            assertEquals((i + 1) * INTERVAL, pacer.nextElapsed());
        }
        assertEquals(100, records.size());
        for (int i = 0; i < 100; i++) assertEquals(i * INTERVAL, records.get(i).actualElapsed());
    }

    @Test void lateAdmissionCannotBeRebasedIntoSuccessfulDrain() throws Exception {
        var clock = new AtomicLong();
        var duration = new L6DurationContract(INTERVAL, INTERVAL, INTERVAL);
        var pacer = new L6DeterministicPacer(clock::get, 0, INTERVAL, duration);
        var records = new ArrayList<L6DeterministicPacer.Slot>();
        clock.set(duration.activeEnd() - 1);
        var failure = assertThrows(IllegalStateException.class, () -> pacer.poll(false, slot -> {
            clock.set(duration.activeEnd() + 1); return "late-order";
        }, records::add));
        assertEquals("L6_ADMISSION_CROSSED_PHASE_BOUNDARY", failure.getMessage());
        assertEquals("STOPPED", records.getLast().decision()); assertEquals("late-order", records.getLast().logicalOrderId());
        assertThrows(IllegalStateException.class, () -> L6FormalTrigger.requireBeforeDeadline(42, 42));
        assertThrows(IllegalStateException.class, () -> L6FormalTrigger.requireBeforeDeadline(43, 42));
        assertDoesNotThrow(() -> L6FormalTrigger.requireBeforeDeadline(41, 42));
    }
}
