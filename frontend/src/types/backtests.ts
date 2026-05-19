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
