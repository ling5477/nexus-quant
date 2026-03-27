export interface BacktestPublishListItem {
    backtestRunId: string;
    backtestConfigId: string;
    researchConfigId: string;
    sourceStrategyId: string;
    status: string;
    requestedAt: string;
    finishedAt: string | null;
    publishStatus: string | null;
    publishedAt: string | null;
    targetStrategyDefinitionId: string | null;
    publishName: string | null;
    failureCode: string | null;
    failureMessage: string | null;
}

export interface PublishesListFilters {
    researchConfigId: string;
    backtestConfigId: string;
    sourceStrategyId: string;
    publishStatus: string;
}

export const defaultPublishesListFilters: PublishesListFilters = {
    researchConfigId: '',
    backtestConfigId: '',
    sourceStrategyId: '',
    publishStatus: '',
};

export interface BacktestPublishDetailItem {
    publishRecordId: string;
    backtestRunId: string;
    researchConfigId: string;
    backtestConfigId: string;
    sourceStrategyId: string;
    targetStrategyDefinitionId: string | null;
    publishStatus: string;
    publishName: string | null;
    publishedAt: string | null;
    evaluationSummaryJson: string | null;
    failureCode: string | null;
    failureMessage: string | null;
    publishSnapshotJson: string | null;
}

export interface BacktestPublishRequest {
    displayName?: string;
}
