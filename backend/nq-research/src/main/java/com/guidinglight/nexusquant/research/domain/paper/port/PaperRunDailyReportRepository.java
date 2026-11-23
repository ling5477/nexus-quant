package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaperRunDailyReportRepository {

    void upsert(PaperRunDailyReport report);

    Optional<PaperRunDailyReport> findById(String reportId);

    Optional<PaperRunDailyReport> findByRunIdAndDate(String paperRunId, LocalDate reportDate);

    List<PaperRunDailyReport> listByRunId(String paperRunId);

    int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end);

    /**
     * 批量按 runId 读取日报，返回 runId -> 日报列表（每个 run 的列表口径与 {@link #listByRunId} 一致）。
     * 用于组合看板等聚合读路径，避免在 run 循环内逐 run 查询造成读放大。
     * 约定：runIds 为空返回空 Map；无日报的 run 不作为 key 出现（调用方按缺省空列表处理）。
     *
     * <p>默认实现按 runId 逐个委托 {@link #listByRunId}，仅作为内存/测试仓储的正确性兜底；
     * JDBC 实现以单条参数化 {@code IN (:runIds)} 查询覆盖，真正消除逐 run 查询。
     */
    default Map<String, List<PaperRunDailyReport>> listByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<PaperRunDailyReport>> result = new LinkedHashMap<>();
        for (String runId : runIds) {
            List<PaperRunDailyReport> rows = listByRunId(runId);
            if (rows != null && !rows.isEmpty()) {
                result.put(runId, rows);
            }
        }
        return result;
    }
}
