package com.guidinglight.nexusquant.eval.port;

import com.guidinglight.nexusquant.backtest.model.SimPosition;

import java.util.List;

public interface SimPositionQueryRepository {

    List<SimPosition> listByBacktestRunId(String backtestRunId);
}
