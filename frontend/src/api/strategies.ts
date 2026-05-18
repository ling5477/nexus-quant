import {apiClient} from '@/api/client';
import type {
    StrategyDefinitionListItem,
    StrategyStatusUpdateRequest,
    StrategyVersionCreateRequest,
    StrategyVersionItem,
} from '@/types/strategies';

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
    async listVersions(strategyCode: string): Promise<StrategyVersionItem[]> {
        const {data} = await apiClient.get<StrategyVersionItem[]>(`/strategies/${strategyCode}/versions`);
        return data;
    },
    async createVersion(strategyCode: string, request: StrategyVersionCreateRequest): Promise<StrategyVersionItem> {
        const {data} = await apiClient.post<StrategyVersionItem>(`/strategies/${strategyCode}/versions`, request);
        return data;
    },
    async versionDetail(strategyCode: string, versionId: string): Promise<StrategyVersionItem> {
        const {data} = await apiClient.get<StrategyVersionItem>(`/strategies/${strategyCode}/versions/${versionId}`);
        return data;
    },
};
