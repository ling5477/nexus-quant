package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.backtest.SimPosition;

import java.util.List;

public interface SimPositionQueryRepository {

    List<SimPosition> listByBacktestRunId(String backtestRunId);
}


