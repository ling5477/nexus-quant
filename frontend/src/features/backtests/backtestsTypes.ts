export interface BacktestConfigListItem {
    backtestConfigId: string;
    researchConfigId: string;
    name: string;
    description: string;
    startTime: string;
    endTime: string;
    initialCapital: number | null;
    executionSpec: string;
    evaluationSpec: string;
    strategyVersionId?: string | null;
    strategyVersionSnapshotJson?: string | null;
    paramSnapshotJson?: string | null;
    configSnapshotJson?: string | null;
    datasetId?: string | null;
    datasetSnapshotJson?: string | null;
    configSnapshot: string;
    createdAt: string;
    updatedAt: string;
}

export interface BacktestsListFilters {
    researchConfigId: string;
    backtestConfigId: string;
    name: string;
}

export const defaultBacktestsListFilters: BacktestsListFilters = {
    researchConfigId: '',
    backtestConfigId: '',
    name: '',
};

export interface BacktestConfigCreateRequest {
    researchConfigId: string;
    name: string;
    description?: string;
    startTime: string;
    endTime: string;
    initialCapital: number;
    executionSpec: string;
    evaluationSpec: string;
}

export interface BacktestDatasetBindingRequest {
    datasetId: string;
}

export interface BacktestStrategyVersionBindingRequest {
    strategyVersionId: string;
}

export interface BacktestRunCreateRequest {
    backtestConfigId: string;
}

/**
 * SimPnlSnapshotItem — 回测运行的权益/PnL 快照(对应后端 GET /api/backtest-runs/{runId}/pnl-snapshots)。
 * 数值为账户币种 BigDecimal,JSON 反序列化为 number;snapshotTime 为权益曲线 x 轴(eventTime)。
 */
export interface SimPnlSnapshotItem {
    simPnlSnapshotId: string;
    backtestRunId: string;
    snapshotTime: string;
    cashBalance: number | null;
    positionMarketValue: number | null;
    realizedPnl: number | null;
    unrealizedPnl: number | null;
    totalFee: number | null;
    totalSlippage: number | null;
    equity: number | null;
    netPnl: number | null;
    createdAt: string;
}

export interface BacktestRunDetailItem {
    backtestRunId: string;
    backtestConfigId: string;
    researchConfigId: string;
    sourceStrategyId: string;
    strategyVersionId: string | null;
    strategyVersionSnapshotJson: string | null;
    paramSnapshotJson: string | null;
    configSnapshotJson: string | null;
    datasetSnapshotJson: string | null;
    status: string;
    requestedAt: string;
    startedAt: string | null;
    finishedAt: string | null;
}
