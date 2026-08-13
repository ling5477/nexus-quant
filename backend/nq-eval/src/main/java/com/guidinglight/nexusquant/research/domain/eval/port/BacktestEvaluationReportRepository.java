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

    /**
     * 按研究配置或回测配置筛选评估报告。
     *
     * <p>Why：回测详情必须把 evaluation 限定到当前 config；忽略筛选会把其他配置的指标、
     * run 与权益曲线串到当前页面。默认实现只兼容无筛选的旧调用，持久化实现负责跨 run 事实过滤。
     *
     * @param researchConfigId 研究配置 ID，可空
     * @param backtestConfigId 回测配置 ID，可空
     * @return 当前筛选范围内的评估报告
     */
    default List<BacktestEvaluationReport> list(String researchConfigId, String backtestConfigId) {
        return researchConfigId == null && backtestConfigId == null ? listAll() : List.of();
    }
}


