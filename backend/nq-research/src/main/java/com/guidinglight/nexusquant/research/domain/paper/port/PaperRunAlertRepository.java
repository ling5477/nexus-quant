package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaperRunAlertRepository {

    void insert(PaperRunAlert alert);

    Optional<PaperRunAlert> findById(String alertId);

    List<PaperRunAlert> listByRunId(String paperRunId, String status, String severity);

    boolean updateStatus(String alertId, PaperRunAlertStatus status, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, Instant updatedAt);

    int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end);

    int countCriticalOpenByRunIdAndDateRange(String paperRunId, Instant start, Instant end);

    int countByRunIdAndTypeAndDateRange(String paperRunId, String alertType, Instant start, Instant end);

    /**
     * 批量按 runId 统计未处理（OPEN）告警数，返回 runId -> OPEN 告警计数。
     * 用于组合看板等聚合读路径，只取计数而非完整告警明细，避免在 run 循环内逐 run 加载全部告警行。
     * 约定：runIds 为空返回空 Map；无 OPEN 告警的 run 不作为 key 出现（调用方按缺省 0 处理）。
     *
     * <p>默认实现按 runId 逐个委托 {@link #listByRunId} 并过滤 OPEN，仅作为内存/测试仓储的正确性兜底；
     * JDBC 实现以单条参数化 {@code IN (:runIds)} + {@code GROUP BY} 聚合查询覆盖，真正消除逐 run 查询。
     */
    default Map<String, Long> countOpenByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (String runId : runIds) {
            long openCount = listByRunId(runId, PaperRunAlertStatus.OPEN.name(), null).stream()
                    .filter(a -> a.status() == PaperRunAlertStatus.OPEN)
                    .count();
            if (openCount > 0) {
                result.put(runId, openCount);
            }
        }
        return result;
    }
}
