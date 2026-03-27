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
