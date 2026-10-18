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

export interface PublishPageRequest extends PublishesListFilters {
    limit: number;
    offset: number;
}

/** 分页仍引用原发布及冻结快照，不能把策略存在当作已发布或已运行。 */
export interface PublishPage {
    items: BacktestPublishListItem[];
    total: number;
    limit: number;
    offset: number;
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
