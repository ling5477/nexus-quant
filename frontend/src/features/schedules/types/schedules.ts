export interface StrategyScheduleListItem {
    scheduleJobId: string;
    strategyId: string;
    scheduleType: string;
    cronExpr: string;
    timezone: string;
    enabled: boolean;
    status: string;
    windowConfig: string;
    dedupScope: string;
    exchangeCode: string;
    accountId: number | null;
    tradeEnv: string;
    lastTriggeredAt: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface StrategyScheduleListFilters {
    strategyId: string;
    scheduleType: string;
    status: string;
    enabled: 'all' | 'true' | 'false';
}

export const defaultStrategyScheduleListFilters: StrategyScheduleListFilters = {
    strategyId: '',
    scheduleType: '',
    status: '',
    enabled: 'all',
};

export interface StrategyScheduleStatusUpdateRequest {
    enabled: boolean;
}
