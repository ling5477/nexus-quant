import {apiClient} from '@/api/client';
import type {StrategyRunDetailItem, StrategyRunSummaryItem} from '@/types/runs';

export interface StrategyRunListRequest {
    strategyId?: string;
    scheduleId?: string;
}

export const runsApi = {
    async list(request: StrategyRunListRequest): Promise<StrategyRunSummaryItem[]> {
        const {data} = await apiClient.get<StrategyRunSummaryItem[]>('/strategy-runs', {
            params: request,
        });
        return data;
    },
    async detail(runId: string): Promise<StrategyRunDetailItem> {
        const {data} = await apiClient.get<StrategyRunDetailItem>(`/strategy-runs/${runId}`);
        return data;
    },
};
