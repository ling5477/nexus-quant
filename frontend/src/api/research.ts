import {apiClient} from '@/api/client';
import type {ResearchConfigCreateRequest, ResearchConfigListItem} from '@/types/research';

export const researchApi = {
    async list(sourceStrategyId?: string): Promise<ResearchConfigListItem[]> {
        const {data} = await apiClient.get<ResearchConfigListItem[]>('/research-configs', {
            params: sourceStrategyId ? {sourceStrategyId} : undefined,
        });
        return data;
    },
    async detail(configId: string): Promise<ResearchConfigListItem> {
        const {data} = await apiClient.get<ResearchConfigListItem>(`/research-configs/${configId}`);
        return data;
    },
    async create(request: ResearchConfigCreateRequest): Promise<ResearchConfigListItem> {
        const {data} = await apiClient.post<ResearchConfigListItem>('/research-configs', request);
        return data;
    },
};
