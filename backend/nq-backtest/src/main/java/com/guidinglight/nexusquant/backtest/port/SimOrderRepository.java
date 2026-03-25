package com.guidinglight.nexusquant.backtest.port;

import com.guidinglight.nexusquant.backtest.model.SimOrder;

import java.util.List;

/**
 * SimOrderRepository 负责模拟订单事实持久化。
 */
public interface SimOrderRepository {

    void insert(SimOrder simOrder);

    List<SimOrder> listByBacktestRunId(String backtestRunId);
}
