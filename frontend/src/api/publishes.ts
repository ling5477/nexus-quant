import {apiClient} from '@/api/client';
import type {BacktestPublishDetailItem, BacktestPublishListItem, BacktestPublishRequest} from '@/types/publishes';

export interface PublishListRequest {
    researchConfigId?: string;
    backtestConfigId?: string;
}

export const publishesApi = {
    async list(request: PublishListRequest): Promise<BacktestPublishListItem[]> {
        const {data} = await apiClient.get<BacktestPublishListItem[]>('/backtest-runs', {
            params: request,
        });
        return data;
    },
    async detail(runId: string): Promise<BacktestPublishDetailItem> {
        const {data} = await apiClient.get<BacktestPublishDetailItem>(`/backtest-runs/${runId}/publish`);
        return data;
    },
    async publish(runId: string, request?: BacktestPublishRequest): Promise<BacktestPublishDetailItem> {
        const {data} = await apiClient.post<BacktestPublishDetailItem>(`/backtest-runs/${runId}/publish`, request);
        return data;
    },
};
