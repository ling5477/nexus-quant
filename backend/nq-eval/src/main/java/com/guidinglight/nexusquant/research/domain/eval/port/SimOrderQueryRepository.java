package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.backtest.SimOrder;

import java.util.List;

public interface SimOrderQueryRepository {

    List<SimOrder> listByBacktestRunId(String backtestRunId);
}


