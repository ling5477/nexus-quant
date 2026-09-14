package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** 绝对阶段时间拥有 admission；定时线程只登记边界，不等待采集或业务检查点。 */
final class L6StoragePhaseController implements AutoCloseable {
    static final long MAX_TRANSITION_LATENESS_MILLIS = 2_000;
    static final long MAX_BOUNDARY_LATENESS_MILLIS = 2_000;
    record Boundary(String type, long scheduledElapsedNanos, String phaseBefore, String phaseAfter) {
        String observationPhase() { return type.endsWith("START") ? phaseAfter : phaseBefore; }
    }
    @FunctionalInterface interface Observer { void collect(Boundary boundary) throws Exception; }
    @FunctionalInterface interface Admission { void send() throws Exception; }
    static final class BoundaryPending extends RuntimeException { }
    private static final ObjectMapper JSON = new ObjectMapper();
    private final L6DurationContract duration;
    private final LongSupplier clock;
    private final long start;
    private final Observer observer;
    private final Path evidence;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final ThreadPoolExecutor worker;
    private final Executor observations;
    private final CountDownLatch end = new CountDownLatch(1);
    private final List<Long> admitted = new ArrayList<>();
    private final ObjectNode proof = JSON.createObjectNode();
    private volatile RuntimeException failure;
    private volatile boolean admissionClosed, capacityStopped, drainReady;
    private int next;

    L6StoragePhaseController(L6DurationContract duration, LongSupplier clock, long start,
                             Observer observer, Path evidence) {
        this(duration, clock, start, observer, evidence, null);
    }

    /** 测试可控制任务何时执行，从而分别阻塞 deadline 与 observation 通道。 */
    L6StoragePhaseController(L6DurationContract duration, LongSupplier clock, long start,
                             Observer observer, Path evidence, Executor testExecutor) {
        this.duration = duration; this.clock = clock; this.start = start;
        this.observer = observer; this.evidence = evidence;
        worker = testExecutor == null ? new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(3), new ThreadPoolExecutor.AbortPolicy()) : null;
        observations = worker == null ? testExecutor : worker;
        proof.put("phaseTransitionLimitMillis", MAX_TRANSITION_LATENESS_MILLIS)
                .put("boundaryObservationStartLimitMillis", MAX_BOUNDARY_LATENESS_MILLIS)
                .put("producerCutoffElapsedNanos", duration.activeEnd());
        proof.putArray("events");
    }

    long elapsed() {
        long value = clock.getAsLong() - start;
        if (value < 0) throw new IllegalStateException("L6_MONOTONIC_CLOCK_REGRESSED");
        return value;
    }

    String phase() {
        if (capacityStopped) return "DRAIN";
        var phase = duration.phase(elapsed());
        return phase == L6DurationContract.Phase.ACTIVE ? "MEASUREMENT" : phase.name();
    }

    boolean producerAllowed() { return failure == null && !admissionClosed && elapsed() < duration.activeEnd(); }
    boolean drainReady() { return drainReady; }

    void requireBusinessDispatchReady() {
        checkHealthy();
        // 每个实际派发入口复核；循环开头的phase快照可能已被一次慢查询或命令结果等待跨越。
        if ((capacityStopped || elapsed() >= duration.activeEnd()) && !drainReady) throw new BoundaryPending();
    }

    static String timingFailureResult(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if ("PHASE_TRANSITION_DEADLINE_VIOLATION".equals(cause.getMessage())
                    || "BOUNDARY_OBSERVATION_DEADLINE_VIOLATION".equals(cause.getMessage())) {
                return "BLOCKED / " + cause.getMessage();
            }
        }
        return "FAILED";
    }

    synchronized void admit(long slot, Admission send) throws Exception {
        if (!producerAllowed()) throw new IllegalStateException("L6_ADMISSION_PHASE_CLOSED");
        if (admitted.contains(slot)) throw new IllegalStateException("L6_DUPLICATE_ADMISSION_SLOT");
        // 临界区仅写一条有界命令，不等待其业务结果；子JVM还会在canonical trigger前复核deadline。
        send.send(); admitted.add(slot);
    }

    void start() {
        for (long at : List.of(duration.warmupNanos(), duration.activeEnd(), duration.total())) {
            deadlines.schedule(this::dispatchSafely, Math.max(0, start + at - clock.getAsLong()), TimeUnit.NANOSECONDS);
        }
    }

    private void dispatchSafely() { try { advance(); } catch (RuntimeException error) { reject(error); } }

    /** 逻辑phase不读取next；next仅用于唯一派发三个边界任务及度量controller响应延迟。 */
    synchronized void advance() {
        checkHealthy();
        if (capacityStopped || next == 3) return;
        long[] targets = {duration.warmupNanos(), duration.activeEnd(), duration.total()};
        long now = elapsed(), target = targets[next];
        if (now < target) return;
        long lateness = TimeUnit.NANOSECONDS.toMillis(now - target);
        event("PHASE_DEADLINE", target).put("observedElapsedNanos", now)
                .put("phaseTransitionLatenessMillis", lateness);
        if (next == 1) closeAdmission(target);
        if (now - target > TimeUnit.MILLISECONDS.toNanos(MAX_TRANSITION_LATENESS_MILLIS)) {
            var error = new IllegalStateException("PHASE_TRANSITION_DEADLINE_VIOLATION"); reject(error); throw error;
        }
        int transition = next++;
        submit(() -> {
            if (transition == 0) {
                capture(new Boundary("WARMUP_END", target, "WARMUP", "MEASUREMENT"));
                capture(new Boundary("MEASUREMENT_START", target, "WARMUP", "MEASUREMENT"));
            } else if (transition == 1) {
                capture(new Boundary("MEASUREMENT_END", target, "MEASUREMENT", "DRAIN"));
                synchronized (this) { event("DRAIN_ENTERED", target); }
                capture(new Boundary("DRAIN_START", target, "MEASUREMENT", "DRAIN"));
                drainReady = true;
            } else {
                capture(new Boundary("DRAIN_END", target, "DRAIN", "COMPLETE")); end.countDown();
            }
        });
    }

    private synchronized void closeAdmission(long target) {
        admissionClosed = true;
        event("PRODUCER_ADMISSION_CLOSED", target);
        proof.set("measurementWorkloadSlots", JSON.valueToTree(admitted));
        event("MEASUREMENT_WORKLOAD_FROZEN", target).put("admittedSlots", admitted.size());
    }

    private void capture(Boundary boundary) throws Exception {
        checkHealthy();
        observer.collect(boundary);
        synchronized (this) { event(boundary.type(), boundary.scheduledElapsedNanos()); }
    }

    private void submit(Admission action) {
        try {
            observations.execute(() -> {
                try { action.send(); }
                catch (Exception error) {
                    String reason = "L6_BOUNDARY_COLLECTION_FAILURE";
                    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                        if ("BOUNDARY_OBSERVATION_DEADLINE_VIOLATION".equals(cause.getMessage())) {
                            reason = "BOUNDARY_OBSERVATION_DEADLINE_VIOLATION"; break;
                        }
                    }
                    reject(new IllegalStateException(reason, error));
                }
            });
        } catch (RuntimeException error) { reject(error); throw error; }
    }

    synchronized void capacityStop() {
        if (capacityStopped) return;
        long now = elapsed(); String before = phase(); capacityStopped = true; closeAdmission(now);
        if (next < 2) submit(() -> {
            capture(new Boundary("CAPACITY_RISK_STOP", now, before, "DRAIN"));
            capture(new Boundary("DRAIN_START", now, before, "DRAIN")); drainReady = true;
        });
    }

    void endCapacityDrain() {
        long now = elapsed();
        submit(() -> { capture(new Boundary("DRAIN_END", now, "DRAIN", "COMPLETE")); end.countDown(); });
    }

    void awaitEnd() throws Exception {
        checkHealthy();
        if (!end.await(12, TimeUnit.SECONDS)) {
            var error = new IllegalStateException("BOUNDARY_OBSERVATION_DEADLINE_VIOLATION"); reject(error); throw error;
        }
        checkHealthy();
    }

    private synchronized ObjectNode event(String type, long scheduled) {
        return proof.withArray("events").addObject().put("type", type)
                .put("scheduledElapsedNanos", scheduled).put("recordedElapsedNanos", elapsed());
    }
    private synchronized void reject(RuntimeException error) {
        if (failure == null) failure = error;
        admissionClosed = true; end.countDown();
    }
    void checkHealthy() { if (failure != null) throw failure; }
    synchronized ObjectNode summary() {
        return proof.deepCopy().put("status", failure == null ? "MEASURED" : "UNAVAILABLE")
                .put("admissionClosed", admissionClosed).put("capacityStopped", capacityStopped)
                .put("failure", failure == null ? null : failure.toString());
    }

    @Override public void close() throws Exception {
        deadlines.shutdownNow();
        boolean terminated = deadlines.awaitTermination(5, TimeUnit.SECONDS);
        if (worker != null) {
            worker.shutdown();
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                worker.shutdownNow(); terminated &= worker.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
        Files.writeString(evidence, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary()));
        if (!terminated) throw new IllegalStateException("L6_PHASE_CONTROLLER_SURVIVOR");
        if (failure != null) throw new IllegalStateException("L6_PHASE_CONTROLLER_CLOSED_WITH_FAILURE", failure);
    }
}
