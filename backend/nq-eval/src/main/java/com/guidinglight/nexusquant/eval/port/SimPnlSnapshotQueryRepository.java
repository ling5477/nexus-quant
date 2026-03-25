package com.guidinglight.nexusquant.eval.port;

import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;

import java.util.List;

public interface SimPnlSnapshotQueryRepository {

    List<SimPnlSnapshot> listByBacktestRunId(String backtestRunId);
}
