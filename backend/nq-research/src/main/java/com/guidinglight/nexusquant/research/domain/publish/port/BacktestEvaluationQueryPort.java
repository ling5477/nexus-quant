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
     * 默认实现仅保留非JDBC适配器的兼容委托；不同来源数可能等于运行数，不能据方法名认定没有查询放大。
     * 生产JDBC实现必须使用有界IN查询，并保留与单项读取相同的费用可见性约束。
     * 仅只读，不触发回测 / 评估 / 发布等写动作。
     */
    default Map<String, BacktestEvaluationView> findByBacktestRunIds(
            Collection<String> backtestRunIds) {
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
