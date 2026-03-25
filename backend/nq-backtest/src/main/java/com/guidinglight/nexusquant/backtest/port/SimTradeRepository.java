package com.guidinglight.nexusquant.backtest.port;

import com.guidinglight.nexusquant.backtest.model.SimTrade;

import java.util.List;

/**
 * SimTradeRepository 负责模拟成交事实持久化。
 */
public interface SimTradeRepository {

    void insert(SimTrade simTrade);

    List<SimTrade> listByBacktestRunId(String backtestRunId);
}
