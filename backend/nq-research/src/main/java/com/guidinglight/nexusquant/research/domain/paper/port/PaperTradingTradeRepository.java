package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface PaperTradingTradeRepository {

    void insert(PaperTradingTrade trade);

    List<PaperTradingTrade> listByRunId(String paperRunId);

    /**
     * 批量按 runId 统计成交数，返回 runId -> 成交计数。
     * 用于组合看板等聚合读路径，只取计数而非完整成交明细，避免在 run 循环内逐 run 加载全部成交行。
     * 约定：runIds 为空返回空 Map；无成交的 run 不作为 key 出现（调用方按缺省 0 处理）。
     *
     * <p>默认实现按 runId 逐个委托 {@link #listByRunId}，仅作为内存/测试仓储的正确性兜底；
     * JDBC 实现以单条参数化 {@code IN (:runIds)} + {@code GROUP BY} 聚合查询覆盖，真正消除逐 run 查询。
     */
    default Map<String, Long> countByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (String runId : runIds) {
            long count = listByRunId(runId).size();
            if (count > 0) {
                result.put(runId, count);
            }
        }
        return result;
    }
}
