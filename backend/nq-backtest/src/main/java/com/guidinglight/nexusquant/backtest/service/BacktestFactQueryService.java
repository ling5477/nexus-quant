package com.guidinglight.nexusquant.backtest.service;

import com.guidinglight.nexusquant.backtest.model.SimOrder;
import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;
import com.guidinglight.nexusquant.backtest.model.SimPosition;
import com.guidinglight.nexusquant.backtest.model.SimTrade;
import com.guidinglight.nexusquant.backtest.port.SimOrderRepository;
import com.guidinglight.nexusquant.backtest.port.SimPnlSnapshotRepository;
import com.guidinglight.nexusquant.backtest.port.SimPositionRepository;
import com.guidinglight.nexusquant.backtest.port.SimTradeRepository;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * BacktestFactQueryService 提供 GateF-3 的 run 明细查询入口。
 */
@Service
public class BacktestFactQueryService {

    private final SimOrderRepository simOrderRepository;
    private final SimTradeRepository simTradeRepository;
    private final SimPositionRepository simPositionRepository;
    private final SimPnlSnapshotRepository simPnlSnapshotRepository;

    public BacktestFactQueryService(
            SimOrderRepository simOrderRepository,
            SimTradeRepository simTradeRepository,
            SimPositionRepository simPositionRepository,
            SimPnlSnapshotRepository simPnlSnapshotRepository
    ) {
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

    public List<SimOrder> listOrders(String backtestRunId) {
        return simOrderRepository.listByBacktestRunId(backtestRunId);
    }

    public List<SimTrade> listTrades(String backtestRunId) {
        return simTradeRepository.listByBacktestRunId(backtestRunId);
    }

    public List<SimPosition> listPositions(String backtestRunId) {
        return simPositionRepository.listByBacktestRunId(backtestRunId);
    }

    public List<SimPnlSnapshot> listPnlSnapshots(String backtestRunId) {
        return simPnlSnapshotRepository.listByBacktestRunId(backtestRunId);
    }
}
