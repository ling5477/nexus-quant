package com.guidinglight.nexusquant.research.domain.publish.port;

import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public interface BacktestEvaluationQueryPort {

    Optional<BacktestEvaluationView> findByBacktestRunId(String backtestRunId);

    /**
     * 批量按 backtestRunId 读取评估投影，返回 runId -&gt; view（缺失 / 无值的 runId 不出现在结果中）。
     * <p>
     * 默认实现按 id 逐个委托 {@link #findByBacktestRunId}；id 集合规模由「去重后的 publish/backtest 数」界定
     * （远小于 run 数），不构成 per-run 查询放大。JDBC 实现可覆盖为单条 IN (:ids) 批量查询做进一步优化。
     * 仅只读，不触发回测 / 评估 / 发布等写动作。
     */
    default Map<String, BacktestEvaluationView> findByBacktestRunIds(Collection<String> backtestRunIds) {
        Map<String, BacktestEvaluationView> result = new LinkedHashMap<>();
        if (backtestRunIds == null) {
            return result;
        }
        for (String backtestRunId : backtestRunIds) {
            if (backtestRunId == null || backtestRunId.isBlank()) {
                continue;
            }
            findByBacktestRunId(backtestRunId).ifPresent(view -> result.put(backtestRunId, view));
        }
        return result;
    }
}


