package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;

import java.util.Optional;

/**
 * BacktestEvaluationReportRepository 负责评估报告持久化。
 */
public interface BacktestEvaluationReportRepository {

    void upsert(BacktestEvaluationReport backtestEvaluationReport);

    Optional<BacktestEvaluationReport> findByBacktestRunId(String backtestRunId);
}


