package com.guidinglight.nexusquant.observability.resource;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/** 有界的进程内采样，重启后新身份和新窗口，不补造过去的资源曲线。 */
public final class JvmResourceSampler {
    public static final int SAMPLE_SECONDS = 15;
    public static final int MAX_SAMPLES = 120;
    private final Clock clock;
    private final LongSupplier monotonicNanos;
    private final Probe probe;
    private final String instanceId = UUID.randomUUID().toString();
    private final ArrayDeque<JvmResourceSample> samples = new ArrayDeque<>();
    private Reading previous;
    private long previousNanos;

    public JvmResourceSampler() {
        this(Clock.systemUTC(), System::nanoTime, JvmResourceSampler::readJvm);
    }

    JvmResourceSampler(Clock clock, LongSupplier monotonicNanos, Probe probe) {
        this.clock = clock;
        this.monotonicNanos = monotonicNanos;
        this.probe = probe;
    }

    /** 采样与快照共用对象锁，保护前后计数和120点窗口；首次或无效CPU区间保留缺失值。 */
    public synchronized void sample() {
        Reading reading = probe.read();
        long nanos = monotonicNanos.getAsLong();
        Double cpu = null;
        // 使用单调时钟和累计CPU时间；处理首次采样、不可用、计数回退及处理器配额变化。
        if (previous != null
                && reading.cpuNanos() >= 0
                && previous.cpuNanos() >= 0
                && reading.processors() > 0
                && previous.processors() == reading.processors()) {
            long elapsed = nanos - previousNanos;
            long consumed = reading.cpuNanos() - previous.cpuNanos();
            if (elapsed > 0 && consumed >= 0) {
                double percent = consumed * 100.0 / elapsed / reading.processors();
                if (Double.isFinite(percent) && percent >= 0 && percent <= 100) cpu = percent;
            }
        }
        samples.addLast(
                new JvmResourceSample(
                        clock.instant(),
                        cpu,
                        reading.processors(),
                        reading.heapUsed(),
                        reading.heapCommitted(),
                        reading.heapMax(),
                        reading.nonHeapUsed(),
                        reading.threads(),
                        reading.gcCollections(),
                        reading.gcTime(),
                        reading.uptime()));
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
        previous = reading;
        previousNanos = nanos;
    }

    /** 返回不可变样本副本与实际新鲜度；读取不采样，检查时点不冒充最近测量时点。 */
    public synchronized Snapshot snapshot() {
        List<JvmResourceSample> copy = List.copyOf(samples);
        Instant now = clock.instant();
        JvmResourceSample latest = copy.isEmpty() ? null : copy.getLast();
        String status =
                latest == null
                        ? "WARMING_UP"
                        : latest.measuredAt().isBefore(now.minusSeconds(45))
                                ? "STALE"
                                : latest.processCpuPercent() == null
                                                || latest.heapUsedBytes() == null
                                                || latest.liveThreads() == null
                                        ? "PARTIAL"
                                        : "AVAILABLE";
        return new Snapshot(
                instanceId,
                "JVM_PROCESS",
                status,
                now,
                copy.isEmpty() ? null : copy.getFirst().measuredAt(),
                latest == null ? null : latest.measuredAt(),
                SAMPLE_SECONDS,
                MAX_SAMPLES,
                copy);
    }

    public record Snapshot(
            String instanceId,
            String scope,
            String status,
            Instant checkedAt,
            Instant windowStart,
            Instant lastSampleAt,
            int sampleSeconds,
            int maxSamples,
            List<JvmResourceSample> samples) {}

    @FunctionalInterface
    interface Probe {
        Reading read();
    }

    record Reading(
            long cpuNanos,
            int processors,
            Long heapUsed,
            Long heapCommitted,
            Long heapMax,
            Long nonHeapUsed,
            Integer threads,
            Long gcCollections,
            Long gcTime,
            Long uptime) {}

    private static Reading readJvm() {
        long cpu = -1;
        int processors = Runtime.getRuntime().availableProcessors();
        Long heapUsed = null,
                heapCommitted = null,
                heapMax = null,
                nonHeapUsed = null,
                gcCount = null,
                gcTime = null,
                uptime = null;
        Integer threads = null;
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os)
                cpu = os.getProcessCpuTime();
        } catch (SecurityException | UnsupportedOperationException ignored) {
            /* 当前平台不可提供此指标。 */
        }
        try {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            heapUsed = nonnegative(heap.getUsed());
            heapCommitted = nonnegative(heap.getCommitted());
            heapMax = nonnegative(heap.getMax());
            nonHeapUsed =
                    nonnegative(
                            ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());
        } catch (SecurityException | UnsupportedOperationException ignored) {
            /* 内存失败不阻断其他指标。 */
        }
        try {
            threads = ManagementFactory.getThreadMXBean().getThreadCount();
        } catch (SecurityException | UnsupportedOperationException ignored) {
            /* 不读取线程名称或堆栈。 */
        }
        try {
            var collectors = ManagementFactory.getGarbageCollectorMXBeans();
            boolean countKnown = !collectors.isEmpty(), timeKnown = !collectors.isEmpty();
            long count = 0, time = 0;
            for (var collector : collectors) {
                long currentCount = collector.getCollectionCount(),
                        currentTime = collector.getCollectionTime();
                countKnown &= currentCount >= 0;
                timeKnown &= currentTime >= 0;
                if (currentCount >= 0) count += currentCount;
                if (currentTime >= 0) time += currentTime;
            }
            gcCount = countKnown ? count : null;
            gcTime = timeKnown ? time : null;
        } catch (SecurityException | UnsupportedOperationException ignored) {
            /* 不把部分GC计数当成全部。 */
        }
        try {
            uptime = nonnegative(ManagementFactory.getRuntimeMXBean().getUptime());
        } catch (SecurityException | UnsupportedOperationException ignored) {
            /* 不读取启动参数或环境变量。 */
        }
        return new Reading(
                cpu,
                processors,
                heapUsed,
                heapCommitted,
                heapMax,
                nonHeapUsed,
                threads,
                gcCount,
                gcTime,
                uptime);
    }

    private static Long nonnegative(long value) {
        return value >= 0 ? value : null;
    }
}
