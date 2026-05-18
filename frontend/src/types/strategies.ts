export interface StrategyDefinitionListItem {
    strategyId: string;
    strategyCode: string;
    strategyName: string;
    strategyType: string;
    exchangeCode: string;
    accountId: number | null;
    tradeEnv: string;
    enabled: boolean;
    status: string;
    configSnapshot: string;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface StrategyListFilters {
    strategyCode: string;
    strategyType: string;
    exchangeCode: string;
    tradeEnv: string;
    enabled: 'all' | 'true' | 'false';
}

export const defaultStrategyListFilters: StrategyListFilters = {
    strategyCode: '',
    strategyType: '',
    exchangeCode: '',
    tradeEnv: '',
    enabled: 'all',
};

export interface StrategyStatusUpdateRequest {
    enabled: boolean;
}

export interface StrategyVersionItem {
    strategyVersionId: string;
    strategyCode: string;
    version: number;
    versionName: string;
    status: string;
    paramSnapshotJson: string;
    configSnapshotJson: string;
    sourceSnapshotJson: string;
    checksum: string;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

export interface StrategyVersionCreateRequest {
    versionName: string;
    status?: string;
    paramSnapshotJson?: string;
    configSnapshotJson?: string;
    sourceSnapshotJson?: string;
}
