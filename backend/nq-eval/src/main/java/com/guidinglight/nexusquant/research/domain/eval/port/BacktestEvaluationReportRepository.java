package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;

import java.util.List;
import java.util.Optional;

/**
 * BacktestEvaluationReportRepository 负责评估报告持久化。
 */
public interface BacktestEvaluationReportRepository {

    void upsert(BacktestEvaluationReport backtestEvaluationReport);

    Optional<BacktestEvaluationReport> findByBacktestRunId(String backtestRunId);

    /**
     * 按评估报告 ID 查询详情。
     * Why:
     * GateI-2 增加 `/api/evaluations/{evaluationId}` 独立入口，报告详情不能再只依赖 run ID 路由。
     *
     * @param evalReportId 评估报告 ID
     * @return 评估报告；不存在时为空
     */
    default Optional<BacktestEvaluationReport> findByEvalReportId(String evalReportId) {
        return Optional.empty();
    }

    /**
     * 查询评估报告列表。
     * Why:
     * GateI-2 前端评估页要直接展示核心指标，因此 repository 提供按 run 时间倒序的最小列表；
     * 复杂分页和指标区间筛选留到后续批次。
     *
     * @return 评估报告列表
     */
    default List<BacktestEvaluationReport> listAll() {
        return List.of();
    }
}


