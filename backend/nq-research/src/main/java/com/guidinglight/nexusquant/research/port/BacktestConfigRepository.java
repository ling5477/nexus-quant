package com.guidinglight.nexusquant.research.port;

import com.guidinglight.nexusquant.research.model.BacktestConfig;

import java.util.List;
import java.util.Optional;

/**
 * BacktestConfigRepository 负责 backtest_configs 的持久化访问。
 */
public interface BacktestConfigRepository {

    void insert(BacktestConfig backtestConfig);

    Optional<BacktestConfig> findByBacktestConfigId(String backtestConfigId);

    List<BacktestConfig> listByResearchConfigId(String researchConfigId);
}
