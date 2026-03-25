package com.guidinglight.nexusquant.eval.port;

import com.guidinglight.nexusquant.backtest.model.SimTrade;

import java.util.List;

public interface SimTradeQueryRepository {

    List<SimTrade> listByBacktestRunId(String backtestRunId);
}
