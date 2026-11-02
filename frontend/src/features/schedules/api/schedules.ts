import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import {validateResultPage} from '@/api/result-page';
import type {
    StrategyScheduleListItem,
    StrategyScheduleStatusUpdateRequest,
    StrategySchedulePageRequest,
    StrategySchedulePage,
    StrategyScheduleSummary,
} from '@/features/schedules/types/schedules';

// 旧详情契约仍返回JSON整数；在JSON.parse之前保留账户身份，不能从已舍入number补造。
const exactAccount = {
    transformResponse: [
        (data: unknown) =>
            typeof data === 'string' ? parseExactJson(data, new Set(['accountId'])) : data,
    ],
};
export const schedulesApi = {
    async page(
        request: StrategySchedulePageRequest,
        signal?: AbortSignal,
    ): Promise<StrategySchedulePage> {
        const {data} = await apiClient.get<unknown>('/strategy-schedules/page', {
            params: request,
            signal,
        });
        return validateResultPage<StrategyScheduleSummary>(data, request, (value) => {
            if (!value || typeof value !== 'object') return null;
            const row = value as StrategyScheduleSummary;
            return typeof row.scheduleJobId === 'string' &&
                row.strategyId === request.strategyId &&
                typeof row.enabled === 'boolean' &&
                row.status === (row.enabled ? 'ENABLED' : 'DISABLED') &&
                (!request.scheduleId || row.scheduleJobId === request.scheduleId) &&
                (!request.status || row.status === request.status) &&
                (!request.scheduleType || row.scheduleType === request.scheduleType) &&
                (row.accountId === null || typeof row.accountId === 'string')
                ? row.scheduleJobId
                : null;
        });
    },
    async detail(scheduleId: string, signal?: AbortSignal): Promise<StrategyScheduleListItem> {
        const {data} = await apiClient.get<StrategyScheduleListItem>(
            `/strategy-schedules/${encodeURIComponent(scheduleId)}`,
            {...exactAccount, signal},
        );
        if (data.scheduleJobId !== scheduleId) throw new Error('Invalid schedule detail identity');
        return data;
    },
    async updateStatus(
        scheduleId: string,
        request: StrategyScheduleStatusUpdateRequest,
    ): Promise<StrategyScheduleListItem> {
        const {data} = await apiClient.patch<StrategyScheduleListItem>(
            `/strategy-schedules/${encodeURIComponent(scheduleId)}/status`,
            request,
            exactAccount,
        );
        if (data.scheduleJobId !== scheduleId || data.enabled !== request.enabled)
            throw new Error('Invalid schedule status receipt');
        return data;
    },
};
