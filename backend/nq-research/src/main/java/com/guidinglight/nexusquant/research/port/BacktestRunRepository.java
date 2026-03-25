package com.guidinglight.nexusquant.research.port;

import com.guidinglight.nexusquant.research.model.BacktestRun;
import com.guidinglight.nexusquant.research.model.BacktestRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * BacktestRunRepository 负责 backtest_runs 的持久化访问。
 */
public interface BacktestRunRepository {

    void insert(BacktestRun backtestRun);

    Optional<BacktestRun> findByBacktestRunId(String backtestRunId);

    List<BacktestRun> list(String researchConfigId, String backtestConfigId);

    /**
     * 更新回测执行状态、时间和摘要。
     * Why:
     * GateF-2 需要把 backtest_run 当成最小执行事实载体，因此状态推进和摘要写回必须是同一条持久化链路。
     */
    boolean updateExecution(
            String backtestRunId,
            BacktestRunStatus status,
            Instant startedAt,
            Instant finishedAt,
            String failureCode,
            String failureMessage,
            String summaryJson,
            Instant updatedAt
    );
}
