import {apiClient} from '@/api/client';
import type {StrategyDefinitionListItem, StrategyStatusUpdateRequest} from '@/types/strategies';

export const strategiesApi = {
    async list(): Promise<StrategyDefinitionListItem[]> {
        const {data} = await apiClient.get<StrategyDefinitionListItem[]>('/strategies');
        return data;
    },
    async detail(strategyCode: string): Promise<StrategyDefinitionListItem> {
        const {data} = await apiClient.get<StrategyDefinitionListItem>(`/strategies/${strategyCode}`);
        return data;
    },
    async updateStatus(strategyCode: string, request: StrategyStatusUpdateRequest): Promise<StrategyDefinitionListItem> {
        const {data} = await apiClient.patch<StrategyDefinitionListItem>(`/strategies/${strategyCode}/status`, request);
        return data;
    },
};
