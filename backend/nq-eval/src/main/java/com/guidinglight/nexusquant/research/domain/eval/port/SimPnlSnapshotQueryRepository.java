package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;

import java.util.List;

public interface SimPnlSnapshotQueryRepository {

    List<SimPnlSnapshot> listByBacktestRunId(String backtestRunId);
}


