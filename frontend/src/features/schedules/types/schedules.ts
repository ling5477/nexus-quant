import type {ResultPage} from '@/api/result-page';

/** 概览不携带窗口JSON；详情中的账户ID也按原始JSON词法保留字符串。 */
export interface StrategyScheduleSummary {
    scheduleJobId: string;
    strategyId: string;
    scheduleType: string;
    cronExpr: string;
    timezone: string;
    enabled: boolean;
    status: 'ENABLED' | 'DISABLED';
    exchangeCode: string;
    accountId: string | null;
    tradeEnv: string;
    lastTriggeredAt: string | null;
    createdAt: string;
    updatedAt: string;
}
export interface StrategyScheduleListItem extends StrategyScheduleSummary {
    windowConfig: string;
    dedupScope: string;
}
export interface StrategyScheduleListFilters {
    strategyId: string;
    scheduleId: string;
    status: string;
    scheduleType: string;
}
export interface StrategySchedulePageRequest extends StrategyScheduleListFilters {
    limit: number;
    offset: number;
}
export type StrategySchedulePage = ResultPage<StrategyScheduleSummary>;
export const defaultStrategyScheduleListFilters: StrategyScheduleListFilters = {
    strategyId: '',
    scheduleId: '',
    status: '',
    scheduleType: '',
};
export interface StrategyScheduleStatusUpdateRequest {
    enabled: boolean;
}
