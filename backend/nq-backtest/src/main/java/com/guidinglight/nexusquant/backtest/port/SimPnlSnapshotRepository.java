package com.guidinglight.nexusquant.backtest.port;

import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;

import java.util.List;

/**
 * SimPnlSnapshotRepository 负责模拟 PnL 快照持久化。
 */
public interface SimPnlSnapshotRepository {

    void insert(SimPnlSnapshot simPnlSnapshot);

    List<SimPnlSnapshot> listByBacktestRunId(String backtestRunId);
}
