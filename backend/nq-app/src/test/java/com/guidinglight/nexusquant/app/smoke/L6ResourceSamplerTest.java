package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 可控时钟逐拍验证全部来源；真实只读端点另证不依赖重型命令锁。 */
class L6ResourceSamplerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;
    private final AtomicLong nanos = new AtomicLong();
    private final Map<String, Set<String>> required = Map.of(
            "nq0", L6RuntimeResources.ACTOR_FIELDS, "nq1", L6RuntimeResources.ACTOR_FIELDS,
            "venue", Set.of("queue", "capacity"), "postgres", Set.of("connections", "backlog", "auditRows"),
            "os", Set.of("handles", "fd"), "files", Set.of("logBytes", "logDeltaBytes", "ownedTempFileCount", "ownedTempBytes"),
            "ownership", Set.of("ownedProcessCount", "ownedContainerCount"));

    private Map<String, L6ResourceSampler.Collector> collectors(AtomicInteger calls) {
        var result = new LinkedHashMap<String, L6ResourceSampler.Collector>();
        required.keySet().stream().sorted().forEach(key -> result.put(key, stamp -> {
            calls.incrementAndGet();
            ObjectNode values = JSON.createObjectNode();
            required.get(key).forEach(field -> values.put(field, stamp.sampleIndex()));
            return L6RuntimeResources.measured(stamp, values, required.get(key));
        }));
        return result;
    }

    private L6ResourceSampler sampler(Map<String, L6ResourceSampler.Collector> collectors) {
        return new L6ResourceSampler(collectors, required, nanos::get,
                () -> Instant.EPOCH.plusNanos(nanos.get()), 0, L6DurationContract.forMode(true), directory.resolve("resources.ndjson"));
    }

    @Test void everyTenSecondsAllSourcesHaveFreshIdentityAndSerializedAvailability() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var sampling = sampler(collectors(calls))) {
            for (int i = 0; i < 12; i++) {
                nanos.set(TimeUnit.SECONDS.toNanos(10L * i)); sampling.sample();
                var sample = sampling.latest();
                assertEquals(i, sample.path("sampleIndex").asInt());
                assertEquals(i * 10_000L, sample.path("elapsedMillis").asLong());
                assertEquals(i < 2 ? "WARMUP" : i < 11 ? "ACTIVE" : "DRAIN", sample.path("phase").asText());
                for (String key : required.keySet()) {
                    var source = sample.path("sources").path(key);
                    for (String field : Set.of("sampleIndex", "sampledAt", "elapsedMillis", "phase", "sampleToken")) {
                        assertEquals(sample.path(field), source.path(field));
                    }
                    for (String field : required.get(key)) {
                        assertEquals("MEASURED", source.path("availability").path(field).asText());
                        assertEquals(i, source.path("values").path(field).asInt());
                    }
                }
            }
            sampling.requireComplete();
            var serialized = JSON.readTree(JSON.writeValueAsString(sampling.summary()));
            assertEquals(12, serialized.path("sampleCount").asInt());
            assertEquals(10_000, serialized.path("sampleIntervalMillis").asInt());
            assertEquals(12 * required.size(), calls.get());
        }
        assertEquals(12, Files.readAllLines(directory.resolve("resources.ndjson")).size());
        assertEquals(12, JSON.readTree(directory.resolve("resource-summary.json").toFile()).path("samples").size());
    }

    @Test void missingCollectorValueOrAvailabilityRejectsAndPreservesFailure() throws Exception {
        var collectors = collectors(new AtomicInteger());
        collectors.put("venue", stamp -> new L6ResourceSampler.Observation(stamp,
                JSON.createObjectNode().put("capacity", 16),
                JSON.createObjectNode().put("queue", "UNAVAILABLE").put("capacity", "MEASURED")));
        var sampling = sampler(collectors);
        assertThrows(IllegalStateException.class, sampling::sample);
        assertEquals("UNAVAILABLE", sampling.summary().path("status").asText());
        assertThrows(IllegalStateException.class, sampling::checkHealthy);
        assertThrows(IllegalStateException.class, sampling::close);
        assertTrue(Files.readString(directory.resolve("resources.ndjson")).contains("UNAVAILABLE"));
    }

    @Test void previousObservationCannotBeRelabeledAsCurrentSample() throws Exception {
        var collectors = collectors(new AtomicInteger());
        L6ResourceSampler.Observation[] cached = {null};
        collectors.put("venue", stamp -> {
            if (cached[0] == null) cached[0] = L6RuntimeResources.measured(stamp,
                    JSON.createObjectNode().put("queue", 0).put("capacity", 16), required.get("venue"));
            return cached[0];
        });
        var sampling = sampler(collectors);
        sampling.sample(); nanos.set(TimeUnit.SECONDS.toNanos(10));
        assertThrows(IllegalStateException.class, sampling::sample);
        assertTrue(sampling.summary().toString().contains("L6_STALE_RESOURCE_OBSERVATION"));
        assertThrows(IllegalStateException.class, sampling::close);
    }

    @Test void missedSlotCannotBeBackfilledOrCountedAsComplete() throws Exception {
        var sampling = sampler(collectors(new AtomicInteger()));
        sampling.sample();
        assertThrows(IllegalStateException.class, sampling::requireComplete);
        nanos.set(TimeUnit.SECONDS.toNanos(20));
        assertThrows(IllegalStateException.class, sampling::sample);
        assertTrue(sampling.summary().toString().contains("L6_RESOURCE_CADENCE_VIOLATION"));
        assertThrows(IllegalStateException.class, sampling::close);
    }

    @Test void slowCollectorCannotProduceAcceptedTenSecondSample() throws Exception {
        var collectors = collectors(new AtomicInteger());
        var original = collectors.get("venue");
        collectors.put("venue", stamp -> { nanos.addAndGet(TimeUnit.SECONDS.toNanos(9)); return original.collect(stamp); });
        var sampling = sampler(collectors);
        assertThrows(IllegalStateException.class, sampling::sample);
        assertTrue(sampling.summary().toString().contains("L6_RESOURCE_COLLECTION_OVERRUN"));
        assertThrows(IllegalStateException.class, sampling::close);
    }

    @Test void blockedHeavyCheckpointDoesNotBlockTenSecondSourcesOrReadOnlyEndpoint() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        AtomicInteger observations = new AtomicInteger();
        try (var command = L6Measurements.commands();
             var endpoint = new L6MetricsEndpoint(() -> JSON.createObjectNode().put("freshCounter", observations.incrementAndGet()));
             var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            var checkpoint = command.submit(() -> { entered.countDown(); return release.await(5, TimeUnit.SECONDS); });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            try {
                var collectors = collectors(new AtomicInteger());
                collectors.put("venue", stamp -> {
                    var request = HttpRequest.newBuilder(URI.create(endpoint.endpoint() + "/metrics"))
                            .timeout(Duration.ofSeconds(1)).header("X-L6-Sample", stamp.token()).GET().build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode());
                    var value = (ObjectNode) JSON.readTree(response.body());
                    assertEquals(stamp.token(), value.path("sampleToken").asText());
                    assertEquals(stamp.sampleIndex() + 1, value.path("observationSequence").asLong());
                    assertEquals(stamp.sampleIndex() + 1, value.path("freshCounter").asLong());
                    return L6RuntimeResources.measured(stamp, value.put("queue", 0).put("capacity", 16), required.get("venue"));
                });
                try (var sampling = sampler(collectors)) {
                    for (int i = 0; i <= 3; i++) {
                        nanos.set(TimeUnit.SECONDS.toNanos(i * 10L)); sampling.sample();
                        assertFalse(checkpoint.isDone());
                    }
                    assertEquals(4, sampling.summary().path("sampleCount").asInt());
                }
            } finally { release.countDown(); }
            assertTrue(checkpoint.get(2, TimeUnit.SECONDS));
            assertEquals(4, observations.get());
        } finally { release.countDown(); }
    }
}
