package com.guidinglight.nexusquant.backtest.service;

import com.guidinglight.nexusquant.backtest.model.HistoricalBar;
import com.guidinglight.nexusquant.backtest.model.SimOrder;
import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;
import com.guidinglight.nexusquant.backtest.model.SimPosition;
import com.guidinglight.nexusquant.backtest.model.SimTrade;
import com.guidinglight.nexusquant.backtest.port.SimOrderRepository;
import com.guidinglight.nexusquant.backtest.port.SimPnlSnapshotRepository;
import com.guidinglight.nexusquant.backtest.port.SimPositionRepository;
import com.guidinglight.nexusquant.backtest.port.SimTradeRepository;
import com.guidinglight.nexusquant.research.model.BacktestRunStatus;
import com.guidinglight.nexusquant.research.port.BacktestRunRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BacktestExecutionPersistenceService 负责回测执行完成后的本地事实落库。
 * <p>
 * Why:
 * 回测执行本身包含历史数据读取和逐 bar 计算，这些步骤不应包进数据库长事务；
 * 但 run 状态推进与 `sim_*` 事实写入必须在本地数据库边界内原子提交，避免留下半套执行结果。
 */
@Service
public class BacktestExecutionPersistenceService {

    private final BacktestRunRepository backtestRunRepository;
    private final SimOrderRepository simOrderRepository;
    private final SimTradeRepository simTradeRepository;
    private final SimPositionRepository simPositionRepository;
    private final SimPnlSnapshotRepository simPnlSnapshotRepository;

    public BacktestExecutionPersistenceService(
            BacktestRunRepository backtestRunRepository,
            SimOrderRepository simOrderRepository,
            SimTradeRepository simTradeRepository,
            SimPositionRepository simPositionRepository,
            SimPnlSnapshotRepository simPnlSnapshotRepository
    ) {
        this.backtestRunRepository = Objects.requireNonNull(
                backtestRunRepository,
                "backtestRunRepository must not be null"
        );
        this.simOrderRepository = Objects.requireNonNull(simOrderRepository, "simOrderRepository must not be null");
        this.simTradeRepository = Objects.requireNonNull(simTradeRepository, "simTradeRepository must not be null");
        this.simPositionRepository = Objects.requireNonNull(
                simPositionRepository,
                "simPositionRepository must not be null"
        );
        this.simPnlSnapshotRepository = Objects.requireNonNull(
                simPnlSnapshotRepository,
                "simPnlSnapshotRepository must not be null"
        );
    }

    /**
     * 把 run 标记为 PREPARING。
     * Why:
     * 历史数据加载与内存计算可能持续一段时间，先落 PREPARING 可以明确说明“运行已进入受控执行阶段，
     * 但最终事实尚未提交”，避免调用方把 CREATED 误判成还未启动。
     */
    @Transactional
    public void markPreparing(String backtestRunId, Instant executionStartedAt) {
        requireRunUpdated(backtestRunRepository.updateExecution(
                backtestRunId,
                BacktestRunStatus.PREPARING,
                executionStartedAt,
                null,
                null,
                null,
                "{}",
                executionStartedAt
        ), backtestRunId);
    }

    /**
     * 原子提交成功执行结果。
     * Why:
     * `RUNNING -> sim_* facts -> SUCCEEDED` 必须在同一事务里提交，
     * 否则会出现 run 已经成功、但子事实只写了一部分的假成功状态。
     */
    @Transactional
    public void persistSuccess(
            String backtestRunId,
            Instant executionStartedAt,
            Instant executionFinishedAt,
            List<SimOrder> simOrders,
            List<SimTrade> simTrades,
            List<SimPosition> simPositions,
            List<SimPnlSnapshot> simPnlSnapshots,
            String summaryJson
    ) {
        requireRunUpdated(backtestRunRepository.updateExecution(
                backtestRunId,
                BacktestRunStatus.RUNNING,
                executionStartedAt,
                null,
                null,
                null,
                "{}",
                executionStartedAt
        ), backtestRunId);
        simOrders.forEach(simOrderRepository::insert);
        simTrades.forEach(simTradeRepository::insert);
        simPositions.forEach(simPositionRepository::upsert);
        simPnlSnapshots.forEach(simPnlSnapshotRepository::insert);
        requireRunUpdated(backtestRunRepository.updateExecution(
                backtestRunId,
                BacktestRunStatus.SUCCEEDED,
                executionStartedAt,
                executionFinishedAt,
                null,
                null,
                summaryJson,
                executionFinishedAt
        ), backtestRunId);
    }

    /**
     * 写回失败终态。
     * Why:
     * 失败状态与失败摘要允许独立于成功事务提交，
     * 这样在成功事务回滚后仍能给 run 留下明确、可解释的失败证据。
     */
    @Transactional
    public void markFailed(
            String backtestRunId,
            Instant executionStartedAt,
            Instant executionFinishedAt,
            String failureCode,
            String failureMessage,
            String summaryJson
    ) {
        requireRunUpdated(backtestRunRepository.updateExecution(
                backtestRunId,
                BacktestRunStatus.FAILED,
                executionStartedAt,
                executionFinishedAt,
                failureCode,
                failureMessage,
                summaryJson,
                executionFinishedAt
        ), backtestRunId);
    }

    private void requireRunUpdated(boolean updated, String backtestRunId) {
        if (!updated) {
            throw new IllegalArgumentException("backtest run not found: " + backtestRunId);
        }
    }
}
