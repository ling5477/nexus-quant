package com.guidinglight.nexusquant.research.domain.backtest.port;

import com.guidinglight.nexusquant.research.domain.backtest.SimOrder;

import java.util.List;

/**
 * SimOrderRepository 负责模拟订单事实持久化。
 */
public interface SimOrderRepository {

    void insert(SimOrder simOrder);

    List<SimOrder> listByBacktestRunId(String backtestRunId);
}


