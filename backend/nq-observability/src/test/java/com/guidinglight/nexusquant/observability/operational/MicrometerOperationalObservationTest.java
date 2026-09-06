package com.guidinglight.nexusquant.observability.operational;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static com.guidinglight.nexusquant.observability.operational.OperationalObservation.Operation.*;
import static com.guidinglight.nexusquant.observability.operational.OperationalObservation.Signal.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MicrometerOperationalObservationTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    @org.junit.jupiter.api.AfterEach
    void closeRegistry() { registry.close(); }

    @Test
    void timestampsCountersAndTagsRemainBoundedAcrossRepeatedConcurrentExecution() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            Clock clock = mock(Clock.class);
            when(clock.instant()).thenReturn(Instant.ofEpochSecond(100));
            var meters = new MicrometerOperationalObservation(registry, clock);
            for (int i = 0; i < 200; i++) executor.submit(() -> {
                for (var operation : OperationalObservation.Operation.values()) {
                    if (operation == CRITICAL_ALERT) { meters.record(operation, EMITTED, 1); continue; }
                    meters.record(operation, ATTEMPT, 1);
                    meters.record(operation, SUCCESS, 1);
                }
            });
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            when(clock.instant()).thenReturn(Instant.ofEpochSecond(200));
            for (var operation : OperationalObservation.Operation.values()) {
                if (operation == CRITICAL_ALERT) continue;
                meters.record(operation, FAILURE, 1);
                assertEquals(200, registry.get("nq.operational.executions").tags("operation", operation.operation(), "result", "success").counter().count());
                assertEquals(100, registry.get("nq.operational.last.success").tag("operation", operation.operation()).gauge().value());
                assertEquals(200, registry.get("nq.operational.last.failure").tag("operation", operation.operation()).gauge().value());
            }
            assertEquals(200, registry.get("nq.operational.alerts").counter().count());
            int count = registry.getMeters().size();
            meters.record(LEDGER_RECONCILE, SUCCESS, 1);
            assertEquals(count, registry.getMeters().size());
            for (Meter meter : registry.getMeters()) {
                for (var tag : meter.getId().getTags()) {
                    assertTrue(Set.of("component", "operation", "result").contains(tag.getKey()));
                    assertFalse(tag.getValue().contains("1001"));
                }
            }
            assertTrue(count < 50, "fixed enum combinations only");
        }
    }

    @Test
    void unknownAndLastObservedSnapshotDoNotInventLiveBacklog() {
        {
            var meters = new MicrometerOperationalObservation(registry, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            assertTrue(registry.getMeters().isEmpty());
            assertTrue(meters.snapshot().toString().contains("observed=false"));
            meters.record(LEDGER_RECONCILE, UNRESOLVED_SNAPSHOT, 3);
            meters.record(LEDGER_RECONCILE, FAILURE, 1);
            assertEquals(3, registry.get("nq.operational.unresolved.snapshot").gauge().value());
            meters.record(LEDGER_RECONCILE, UNRESOLVED_SNAPSHOT, 0);
            assertEquals(0, registry.get("nq.operational.unresolved.snapshot").gauge().value());
            assertNull(registry.find("nq.operational.pending").gauge());
        }
    }

    @Test
    void brokenRegistryCannotEscapeTelemetryShield() {
        {
            registry.config().meterFilter(new MeterFilter() {
                @Override public Meter.Id map(Meter.Id id) { throw new IllegalStateException("synthetic failure"); }
            });
            var shield = new SafeOperationalObservation(new MicrometerOperationalObservation(registry, Clock.systemUTC()));
            assertDoesNotThrow(() -> shield.record(LEDGER_RECOVERY, FAILURE, 1));
            assertDoesNotThrow(() -> shield.record(LEDGER_RECOVERY, SUCCESS, 1));
        }
    }
}
