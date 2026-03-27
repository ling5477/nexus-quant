import {apiClient} from '@/api/client';
import type {BacktestConfigCreateRequest, BacktestConfigListItem} from '@/types/backtests';

export const backtestsApi = {
    async list(researchConfigId?: string): Promise<BacktestConfigListItem[]> {
        const {data} = await apiClient.get<BacktestConfigListItem[]>('/backtest-configs', {
            params: researchConfigId ? {researchConfigId} : undefined,
        });
        return data;
    },
    async detail(configId: string): Promise<BacktestConfigListItem> {
        const {data} = await apiClient.get<BacktestConfigListItem>(`/backtest-configs/${configId}`);
        return data;
    },
    async create(request: BacktestConfigCreateRequest): Promise<BacktestConfigListItem> {
        const {data} = await apiClient.post<BacktestConfigListItem>('/backtest-configs', request);
        return data;
    },
};
