package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.util.stream.Collectors;

/** 导出JVM记录的真实GC后用量，不强制GC，也不把普通heap最小值标成GC低谷。 */
final class L6GcEvidence {
    static ArrayNode read() {
        var out = new ObjectMapper().createArrayNode();
        var heapPools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(p -> p.getType() == MemoryType.HEAP).map(p -> p.getName()).collect(Collectors.toSet());
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            var row = out.addObject().put("name", bean.getName()).put("count", bean.getCollectionCount())
                    .put("timeMillis", bean.getCollectionTime());
            if (!(bean instanceof com.sun.management.GarbageCollectorMXBean extended)) {
                throw new IllegalStateException("GC_POST_COLLECTION_MEASUREMENT_UNAVAILABLE");
            }
            var info = extended.getLastGcInfo();
            if (info == null) row.put("lastGcStatus", "NO_GC_OBSERVED");
            else {
                if (!info.getMemoryUsageAfterGc().keySet().containsAll(heapPools) || heapPools.isEmpty()) {
                    throw new IllegalStateException("GC_HEAP_POOL_MEASUREMENT_UNAVAILABLE");
                }
                long used = heapPools.stream().mapToLong(p -> info.getMemoryUsageAfterGc().get(p).getUsed()).sum();
                row.put("lastGcStatus", "MEASURED").putObject("lastGc")
                        .put("id", info.getId()).put("startUptimeMillis", info.getStartTime())
                        .put("endUptimeMillis", info.getEndTime()).put("durationMillis", info.getDuration())
                        .put("heapUsedAfterGc", used);
            }
        }
        return out;
    }
}
