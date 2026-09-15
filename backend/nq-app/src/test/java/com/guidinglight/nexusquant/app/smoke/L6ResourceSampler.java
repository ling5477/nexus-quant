package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** 单一绝对采样时钟；过期、漏拍和采集失败保留原记录并阻断资格，不补写旧值。 */
final class L6ResourceSampler implements AutoCloseable {
    static final long INTERVAL_MILLIS = 10_000;
    static final long MAX_START_LAG_MILLIS = 2_000;
    static final long MAX_COLLECTION_MILLIS = 8_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    record Stamp(long sampleIndex, String sampledAt, long elapsedMillis, String phase, String token) {
        ObjectNode json() {
            return JSON.createObjectNode().put("sampleIndex", sampleIndex).put("sampledAt", sampledAt)
                    .put("elapsedMillis", elapsedMillis).put("phase", phase).put("sampleToken", token);
        }
    }
    record Observation(Stamp stamp, ObjectNode values, ObjectNode availability) { }
    @FunctionalInterface interface Collector { Observation collect(Stamp stamp) throws Exception; }

    private final Map<String, Collector> collectors;
    private final Map<String, java.util.Set<String>> required;
    private final LongSupplier nanoTime;
    private final Supplier<Instant> wallTime;
    private final long startedNanos;
    private final L6SamplingSchedule duration;
    private final String identity = UUID.randomUUID().toString();
    private final Path output;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ObjectNode summary = JSON.createObjectNode();
    private long index;
    private long boundaryIndex;
    private java.util.function.Consumer<ObjectNode> observer = record -> { };
    private long maximumLag;
    private long maximumCollection;
    private volatile RuntimeException failure;
    private ObjectNode latest;
    private final long periodicCap;
    private final boolean durationDerived;
    private final Object collectionLock;

    L6ResourceSampler(Map<String, Collector> collectors, Map<String, java.util.Set<String>> required,
                      LongSupplier nanoTime, Supplier<Instant> wallTime, long startedNanos,
                      L6SamplingSchedule duration, Path output) {
        this(collectors,required,nanoTime,wallTime,startedNanos,duration,output,false,null);
    }

    /** B入口按时长派生边界；共享生命周期锁只覆盖一次采集，不覆盖进程启动等待。 */
    L6ResourceSampler(Map<String, Collector> collectors, Map<String, java.util.Set<String>> required,
                      LongSupplier nanoTime, Supplier<Instant> wallTime, long startedNanos,
                      L6SamplingSchedule duration, Path output, boolean durationDerived, Object collectionLock) {
        if (collectors.isEmpty() || !collectors.keySet().equals(required.keySet())) {
            throw new IllegalArgumentException("mandatory collector topology");
        }
        this.collectors = new LinkedHashMap<>(collectors);
        this.required = Map.copyOf(required);
        this.nanoTime = nanoTime;
        this.wallTime = wallTime;
        this.startedNanos = startedNanos;
        this.duration = duration;
        this.output = output;
        this.durationDerived=durationDerived;
        this.periodicCap=durationDerived ? Math.ceilDiv(duration.total(),TimeUnit.MILLISECONDS.toNanos(INTERVAL_MILLIS)) : 500;
        if (periodicCap<=0 || periodicCap>1080) throw new IllegalArgumentException("L6 sampling duration bound");
        this.collectionLock=collectionLock==null ? this : collectionLock;
        summary.put("sampleIntervalMillis", INTERVAL_MILLIS).put("maxStartLagMillisAllowed", MAX_START_LAG_MILLIS)
                .put("maxCollectionMillisAllowed", MAX_COLLECTION_MILLIS);
        summary.putArray("samples");
    }

    void start() {
        scheduler.scheduleAtFixedRate(() -> {
            if (failure != null || nanoTime.getAsLong() - startedNanos >= duration.total()) return;
            try { sample(); } catch (RuntimeException error) { failure = error; }
        }, Math.max(0, index * INTERVAL_MILLIS - TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - startedNanos)), INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    synchronized void sample() { synchronized(collectionLock) { collectRecord(null, null, null, null); } }

    synchronized ObjectNode boundary(String name, String phase) {
        return collectRecord(name, phase, null, null);
    }

    synchronized ObjectNode boundaryAt(String name, String phase, long targetNanos) {
        return collectRecord(name, phase, TimeUnit.NANOSECONDS.toMillis(targetNanos), null);
    }

    synchronized ObjectNode boundaryAt(L6StoragePhaseController.Boundary boundary) {
        return collectRecord(boundary.type(), boundary.observationPhase(),
                TimeUnit.NANOSECONDS.toMillis(boundary.scheduledElapsedNanos()), boundary);
    }

    synchronized void observeWith(java.util.function.Consumer<ObjectNode> listener) { observer = listener; }

    private ObjectNode collectRecord(String boundary, String boundaryPhase, Long targetMillis,
                                     L6StoragePhaseController.Boundary timing) {
        checkHealthy();
        long begin = nanoTime.getAsLong();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(begin - startedNanos);
        long lag = elapsed - index * INTERVAL_MILLIS;
        Stamp stamp = new Stamp(boundary == null ? index : -(++boundaryIndex), wallTime.get().toString(), elapsed,
                boundary == null ? duration.samplePhase(begin - startedNanos) : boundaryPhase,
                identity + ":" + (boundary == null ? index : periodicCap + boundaryIndex));
        ObjectNode record = stamp.json().put("scheduledElapsedMillis", index * INTERVAL_MILLIS)
                .put("sampleType", boundary == null ? "PERIODIC" : "PHASE_BOUNDARY");
        if (boundary != null) {
            record.put("boundary", boundary).put("scheduledElapsedMillis", targetMillis == null ? elapsed : targetMillis);
            var names = record.putArray("boundaryNames");
            for (String name : boundary.split("/")) names.add(name);
            if (targetMillis != null) record.put("boundaryStartLagMillis", elapsed - targetMillis);
            long targetNanos = timing == null ? TimeUnit.MILLISECONDS.toNanos(targetMillis == null ? elapsed : targetMillis)
                    : timing.scheduledElapsedNanos();
            record.put("boundaryType", boundary).put("scheduledElapsedNanos", targetNanos)
                    .put("observedElapsedNanos", begin - startedNanos)
                    .put("latenessMillis", (begin - startedNanos - targetNanos) / 1_000_000.0)
                    .put("boundaryObservationLatenessMillis", (begin - startedNanos - targetNanos) / 1_000_000.0)
                    .put("phaseBefore", timing == null ? boundaryPhase : timing.phaseBefore())
                    .put("phaseAfter", timing == null ? boundaryPhase : timing.phaseAfter());
        } else {
            record.put("periodicSamplerLatenessMillis", lag);
        }
        ObjectNode sources = record.putObject("sources");
        try {
            if (targetMillis != null && (record.path("latenessMillis").asDouble() < 0
                    || record.path("latenessMillis").asDouble() > L6StoragePhaseController.MAX_BOUNDARY_LATENESS_MILLIS)) {
                throw new IllegalStateException("BOUNDARY_OBSERVATION_DEADLINE_VIOLATION");
            }
            if ((index >= periodicCap && (!durationDerived || boundary == null)) || boundaryIndex > 12 || (boundary == null && (lag < 0 || lag > MAX_START_LAG_MILLIS))) {
                throw new IllegalStateException("L6_RESOURCE_CADENCE_VIOLATION");
            }
            if (boundary == null) maximumLag = Math.max(maximumLag, lag);
            for (var entry : collectors.entrySet()) {
                ObjectNode source = sources.putObject(entry.getKey());
                try {
                    Observation observation = entry.getValue().collect(stamp);
                    if (observation == null || !stamp.equals(observation.stamp())) {
                        throw new IllegalStateException("L6_STALE_RESOURCE_OBSERVATION");
                    }
                    source.setAll(observation.stamp().json());
                    source.set("values", observation.values());
                    source.set("availability", observation.availability());
                    for (String key : required.get(entry.getKey())) {
                        String state = observation.availability().path(key).asText();
                        if (!"MEASURED".equals(state) && !"NOT_APPLICABLE".equals(state)) {
                            throw new IllegalStateException("L6_MANDATORY_MEASUREMENT_UNAVAILABLE: " + key);
                        }
                        if (!observation.values().hasNonNull(key)) {
                            throw new IllegalStateException("L6_MANDATORY_MEASUREMENT_UNAVAILABLE: " + key);
                        }
                        // 只有互斥平台计数允许不适用；不允许池、队列或业务测量获得豁免。
                        if ("NOT_APPLICABLE".equals(state) && !key.equals("handles") && !key.equals("fd")) {
                            throw new IllegalStateException("L6_INVALID_MEASUREMENT_EXEMPTION");
                        }
                    }
                    source.put("status", "MEASURED");
                } catch (Exception error) {
                    source.put("status", "UNAVAILABLE").put("error", error.toString());
                    throw error;
                }
            }
            long collection = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - begin);
            maximumCollection = Math.max(maximumCollection, collection);
            record.put("collectionMillis", collection);
            if (collection > MAX_COLLECTION_MILLIS) throw new IllegalStateException(boundary == null
                    ? "L6_RESOURCE_COLLECTION_OVERRUN" : "BOUNDARY_OBSERVATION_DEADLINE_VIOLATION");
            record.put("status", "MEASURED");
            observer.accept(record.deepCopy());
            latest = record.deepCopy();
            if (boundary == null) index++;
        } catch (Exception error) {
            for (String key : collectors.keySet()) {
                if (!sources.has(key)) {
                    ObjectNode missing = sources.putObject(key);
                    missing.setAll(stamp.json());
                    missing.put("status", "UNAVAILABLE").put("reason", "SAMPLE_REJECTED_BEFORE_COLLECTION");
                    ObjectNode states = missing.putObject("availability");
                    required.get(key).forEach(field -> states.put(field, "UNAVAILABLE"));
                }
            }
            record.put("status", "UNAVAILABLE").put("error", error.toString());
            failure = new IllegalStateException("L6_RESOURCE_QUALIFICATION_REJECTED", error);
        } finally {
            summary.withArray("samples").add(record.deepCopy());
            try {
                Files.writeString(output, JSON.writeValueAsString(record) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception error) { failure = new IllegalStateException("L6_RESOURCE_EVIDENCE_WRITE_FAILED", error); }
        }
        checkHealthy();
        return record.deepCopy();
    }

    void checkHealthy() { if (failure != null) throw failure; }

    synchronized void requireComplete() {
        checkHealthy();
        long expected = (TimeUnit.NANOSECONDS.toMillis(duration.total()) + INTERVAL_MILLIS - 1) / INTERVAL_MILLIS;
        if (index != expected) throw new IllegalStateException("L6_RESOURCE_SAMPLE_COVERAGE_INCOMPLETE");
    }

    synchronized ObjectNode latest() {
        checkHealthy();
        if (latest == null) throw new IllegalStateException("L6_NO_RESOURCE_SAMPLE");
        return latest.deepCopy();
    }

    synchronized ObjectNode summary() {
        return summary.deepCopy().put("sampleCount", index).put("boundarySampleCount", boundaryIndex).put("totalObservationCount", index + boundaryIndex).put("maxStartLagMillis", maximumLag)
                .put("maxCollectionMillis", maximumCollection).put("status", failure == null ? "MEASURED" : "UNAVAILABLE");
    }

    @Override public void close() throws Exception {
        scheduler.shutdown();
        if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("L6_SAMPLER_SURVIVOR");
        }
        Files.writeString(output.resolveSibling("resource-summary.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary()));
        // try-with-resources不能把同一异常对象suppressed到自身；包装保留最初采样原因。
        if (failure != null) throw new IllegalStateException("L6_SAMPLER_CLOSED_WITH_FAILURE", failure);
    }
}
