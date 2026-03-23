package com.guidinglight.nexusquant.core.service.port;

import com.guidinglight.nexusquant.core.model.StrategyRun;
import com.guidinglight.nexusquant.core.model.StrategyRunStatus;

import java.time.Instant;
import java.util.Optional;

/**
 * StrategyRunRepository 定义 strategy_runs 的最小持久化端口。
 */
public interface StrategyRunRepository {

    void insert(StrategyRun strategyRun);

    Optional<StrategyRun> findByStrategyRunId(String strategyRunId);

    boolean updateStatus(String strategyRunId, StrategyRunStatus status, Instant finishedAt, String errorMessage);
}
