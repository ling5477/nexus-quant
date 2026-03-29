package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationView;

import java.util.Optional;

public interface BacktestEvaluationQueryPort {

    Optional<BacktestEvaluationView> findByBacktestRunId(String backtestRunId);
}


