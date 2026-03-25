package com.guidinglight.nexusquant.eval.port;

import com.guidinglight.nexusquant.eval.model.BacktestEvaluationReport;

import java.util.Optional;

/**
 * BacktestEvaluationReportRepository 负责评估报告持久化。
 */
public interface BacktestEvaluationReportRepository {

    void upsert(BacktestEvaluationReport backtestEvaluationReport);

    Optional<BacktestEvaluationReport> findByBacktestRunId(String backtestRunId);
}
