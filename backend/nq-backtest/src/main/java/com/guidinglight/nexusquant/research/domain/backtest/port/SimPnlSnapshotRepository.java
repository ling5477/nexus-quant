package com.guidinglight.nexusquant.research.domain.backtest.port;

import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;

import java.util.List;

/**
 * SimPnlSnapshotRepository 负责模拟 PnL 快照持久化。
 */
public interface SimPnlSnapshotRepository {

    void insert(SimPnlSnapshot simPnlSnapshot);

    List<SimPnlSnapshot> listByBacktestRunId(String backtestRunId);
}


