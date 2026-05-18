import {apiClient} from '@/api/client';
import type {BacktestPublishDetailItem, BacktestPublishListItem, BacktestPublishRequest} from '@/types/publishes';

export interface PublishListRequest {
    researchConfigId?: string;
    backtestConfigId?: string;
    strategyVersionId?: string;
}

export const publishesApi = {
    async list(request: PublishListRequest): Promise<BacktestPublishListItem[]> {
        const {data} = await apiClient.get<BacktestPublishListItem[]>('/publishes', {
            params: {
                strategyVersionId: request.strategyVersionId,
            },
        });
        return data;
    },
    async detail(publishId: string): Promise<BacktestPublishDetailItem> {
        const {data} = await apiClient.get<BacktestPublishDetailItem>(`/publishes/${publishId}`);
        return data;
    },
    async publish(runId: string, request?: BacktestPublishRequest): Promise<BacktestPublishDetailItem> {
        const {data} = await apiClient.post<BacktestPublishDetailItem>('/publishes', request, {
            params: {backtestRunId: runId},
        });
        return data;
    },
};
