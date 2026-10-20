package com.guidinglight.nexusquant.observability.resource;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 验证CPU区间/处理器口径、缺失计数与有界历史；重启后新身份不继承旧样本，真实JVM检查仅覆盖当前进程。 */
class JvmResourceSamplerTest {
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-13T00:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static JvmResourceSampler.Reading reading(long cpu, int processors) {
        return new JvmResourceSampler.Reading(
                cpu, processors, 1024L, 2048L, 4096L, 512L, 8, 0L, 0L, 3000L);
    }

    @Test
    void cpuUsesMeasuredIntervalAndRuntimeProcessorsAndReadDoesNotSample() {
        var clock = new MutableClock();
        var nanos = new AtomicLong();
        var value = new AtomicReference<>(reading(0, 2));
        var sampler = new JvmResourceSampler(clock, nanos::get, value::get);
        assertEquals("WARMING_UP", sampler.snapshot().status());
        sampler.sample();
        assertNull(sampler.snapshot().samples().getFirst().processCpuPercent());
        nanos.set(15_000_000_000L);
        value.set(reading(7_500_000_000L, 2));
        clock.now = clock.now.plusSeconds(15);
        sampler.sample();
        assertEquals(25.0, sampler.snapshot().samples().getLast().processCpuPercent());
        assertEquals("AVAILABLE", sampler.snapshot().status());
        for (int i = 0; i < 20; i++) assertEquals(2, sampler.snapshot().samples().size());
        clock.now = clock.now.plusSeconds(46);
        assertEquals("STALE", sampler.snapshot().status());
    }

    @Test
    void unsupportedCountersAndQuotaChangesRemainMissingInsteadOfZero() {
        var nanos = new AtomicLong();
        var value = new AtomicReference<>(reading(-1, 2));
        var sampler = new JvmResourceSampler(Clock.systemUTC(), nanos::get, value::get);
        sampler.sample();
        nanos.addAndGet(15_000_000_000L);
        sampler.sample();
        assertNull(sampler.snapshot().samples().getLast().processCpuPercent());
        value.set(reading(10, 4));
        nanos.addAndGet(15_000_000_000L);
        sampler.sample();
        assertNull(sampler.snapshot().samples().getLast().processCpuPercent());
        value.set(reading(5, 4));
        nanos.addAndGet(15_000_000_000L);
        sampler.sample();
        assertNull(sampler.snapshot().samples().getLast().processCpuPercent());
        assertEquals("PARTIAL", sampler.snapshot().status());
    }

    @Test
    void historyIsBoundedImmutableAndNewProcessStartsWithoutOldSamples() {
        var clock = new MutableClock();
        var nanos = new AtomicLong();
        var sampler = new JvmResourceSampler(clock, nanos::get, () -> reading(nanos.get() / 2, 2));
        for (int i = 0; i < 145; i++) {
            sampler.sample();
            clock.now = clock.now.plusSeconds(15);
            nanos.addAndGet(15_000_000_000L);
        }
        var snapshot = sampler.snapshot();
        assertEquals(120, snapshot.samples().size());
        assertEquals(Instant.parse("2026-10-13T00:06:15Z"), snapshot.windowStart());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.samples().clear());
        var restarted = new JvmResourceSampler(clock, nanos::get, () -> reading(0, 2));
        assertNotEquals(snapshot.instanceId(), restarted.snapshot().instanceId());
        assertTrue(restarted.snapshot().samples().isEmpty());
    }

    @Test
    void realJvmReportsOnlyNonSensitiveProcessMetrics() {
        var sampler = new JvmResourceSampler();
        sampler.sample();
        var snapshot = sampler.snapshot();
        assertEquals("JVM_PROCESS", snapshot.scope());
        var latest = snapshot.samples().getLast();
        assertTrue(latest.heapUsedBytes() >= 0);
        assertTrue(latest.liveThreads() > 0);
        assertTrue(latest.availableProcessors() > 0);
        assertTrue(latest.uptimeMillis() >= 0);
        assertNull(latest.processCpuPercent());
    }
}
