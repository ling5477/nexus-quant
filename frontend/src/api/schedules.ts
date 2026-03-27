import {apiClient} from '@/api/client';
import type {StrategyScheduleListItem, StrategyScheduleStatusUpdateRequest} from '@/types/schedules';

export const schedulesApi = {
    async list(strategyId: string): Promise<StrategyScheduleListItem[]> {
        const {data} = await apiClient.get<StrategyScheduleListItem[]>('/strategy-schedules', {
            params: {
                strategyId,
            },
        });
        return data;
    },
    async detail(scheduleId: string): Promise<StrategyScheduleListItem> {
        const {data} = await apiClient.get<StrategyScheduleListItem>(`/strategy-schedules/${scheduleId}`);
        return data;
    },
    async updateStatus(scheduleId: string, request: StrategyScheduleStatusUpdateRequest): Promise<StrategyScheduleListItem> {
        const {data} = await apiClient.patch<StrategyScheduleListItem>(`/strategy-schedules/${scheduleId}/status`, request);
        return data;
    },
};
