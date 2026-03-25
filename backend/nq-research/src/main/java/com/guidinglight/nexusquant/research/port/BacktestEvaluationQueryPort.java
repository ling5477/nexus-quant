package com.guidinglight.nexusquant.research.port;

import com.guidinglight.nexusquant.research.model.BacktestEvaluationView;

import java.util.Optional;

public interface BacktestEvaluationQueryPort {

    Optional<BacktestEvaluationView> findByBacktestRunId(String backtestRunId);
}
