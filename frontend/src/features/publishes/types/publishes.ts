export interface BacktestPublishListItem {
    publishRecordId: string;
    backtestRunId: string;
    backtestConfigId: string;
    researchConfigId: string;
    sourceStrategyId: string;
    targetStrategyDefinitionId: string | null;
    strategyVersionId: string | null;
    publishStatus: string;
    publishedAt: string | null;
    publishName: string | null;
    evaluationSummaryJson: string | null;
    failureCode: string | null;
    failureMessage: string | null;
    publishSnapshotJson: string | null;
    versionSnapshotJson: string | null;
}

export interface PublishesListFilters {
    researchConfigId: string;
    backtestConfigId: string;
    sourceStrategyId: string;
    strategyVersionId: string;
    publishStatus: string;
}

export const defaultPublishesListFilters: PublishesListFilters = {
    researchConfigId: '',
    backtestConfigId: '',
    sourceStrategyId: '',
    strategyVersionId: '',
    publishStatus: '',
};

export interface BacktestPublishDetailItem {
    publishRecordId: string;
    backtestRunId: string;
    researchConfigId: string;
    backtestConfigId: string;
    sourceStrategyId: string;
    targetStrategyDefinitionId: string | null;
    strategyVersionId: string | null;
    publishStatus: string;
    publishName: string | null;
    publishedAt: string | null;
    evaluationSummaryJson: string | null;
    failureCode: string | null;
    failureMessage: string | null;
    publishSnapshotJson: string | null;
    versionSnapshotJson: string | null;
}

export interface BacktestPublishRequest {
    displayName?: string;
    strategyVersionId?: string;
}
