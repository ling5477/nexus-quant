import {apiClient} from '@/api/client';
import type {
    BacktestConfigCreateRequest,
    BacktestConfigListItem,
    BacktestDatasetBindingRequest,
    BacktestRunCreateRequest,
    BacktestRunDetailItem,
    BacktestStrategyVersionBindingRequest,
} from '@/types/backtests';

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
    async bindDataset(configId: string, request: BacktestDatasetBindingRequest): Promise<BacktestConfigListItem> {
        const {data} = await apiClient.patch<BacktestConfigListItem>(`/backtest-configs/${configId}/dataset`, request);
        return data;
    },
    async bindStrategyVersion(
        configId: string,
        request: BacktestStrategyVersionBindingRequest,
    ): Promise<BacktestConfigListItem> {
        const {data} = await apiClient.patch<BacktestConfigListItem>(
            `/backtest-configs/${configId}/strategy-version`,
            request,
        );
        return data;
    },
    async createRun(request: BacktestRunCreateRequest): Promise<BacktestRunDetailItem> {
        const {data} = await apiClient.post<BacktestRunDetailItem>('/backtest-runs', request);
        return data;
    },
    async getRun(runId: string): Promise<BacktestRunDetailItem> {
        const {data} = await apiClient.get<BacktestRunDetailItem>(`/backtest-runs/${runId}`);
        return data;
    },
};
