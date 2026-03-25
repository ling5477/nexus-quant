package com.guidinglight.nexusquant.eval.port;

import com.guidinglight.nexusquant.backtest.model.SimOrder;

import java.util.List;

public interface SimOrderQueryRepository {

    List<SimOrder> listByBacktestRunId(String backtestRunId);
}
