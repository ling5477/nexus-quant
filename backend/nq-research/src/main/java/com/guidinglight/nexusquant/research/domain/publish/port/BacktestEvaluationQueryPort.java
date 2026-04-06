package com.guidinglight.nexusquant.research.domain.publish.port;

import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;

import java.util.Optional;

public interface BacktestEvaluationQueryPort {

    Optional<BacktestEvaluationView> findByBacktestRunId(String backtestRunId);
}


