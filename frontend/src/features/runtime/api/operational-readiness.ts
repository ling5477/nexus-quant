import {apiClient} from '@/api/client';
import type {OperationalReadinessResponse} from '@/features/runtime/types/operational-readiness';

/**
 * Operational readiness 只读 API 客户端。
 *
 * Why:
 * 适配器就绪策略只消费运行就绪状态 safe summary，用于展示 disabled capability / startup boundary。
 * 该客户端只封装 GET，不提供任何 mutation 方法。
 */
export const operationalReadinessApi = {
    async getReadiness(): Promise<OperationalReadinessResponse> {
        const {data} = await apiClient.get<OperationalReadinessResponse>('/runtime/operational-readiness');
        return data;
    },
};
