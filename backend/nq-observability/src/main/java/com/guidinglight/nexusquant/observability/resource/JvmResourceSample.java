package com.guidinglight.nexusquant.observability.resource;

import java.time.Instant;

/** 仅测量本进程；null表示指标不可取得，不能作为零负载或健康结论。 */
public record JvmResourceSample(
        Instant measuredAt,
        Double processCpuPercent,
        int availableProcessors,
        Long heapUsedBytes,
        Long heapCommittedBytes,
        Long heapMaxBytes,
        Long nonHeapUsedBytes,
        Integer liveThreads,
        Long gcCollections,
        Long gcTimeMillis,
        Long uptimeMillis) {}
