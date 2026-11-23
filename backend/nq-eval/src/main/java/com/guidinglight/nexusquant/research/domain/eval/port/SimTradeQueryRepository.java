package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import java.util.List;

public interface SimTradeQueryRepository {

    List<SimTrade> listByBacktestRunId(String backtestRunId);
}


