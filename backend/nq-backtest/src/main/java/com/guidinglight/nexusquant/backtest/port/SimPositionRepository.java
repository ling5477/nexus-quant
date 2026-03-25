package com.guidinglight.nexusquant.backtest.port;

import com.guidinglight.nexusquant.backtest.model.SimPosition;

import java.util.List;
import java.util.Optional;

/**
 * SimPositionRepository 负责模拟持仓事实持久化。
 */
public interface SimPositionRepository {

    void upsert(SimPosition simPosition);

    Optional<SimPosition> findByBacktestRunIdAndSymbol(String backtestRunId, String symbol);

    List<SimPosition> listByBacktestRunId(String backtestRunId);
}
