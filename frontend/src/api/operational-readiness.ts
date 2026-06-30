import {apiClient} from '@/api/client';
import type {OperationalReadinessResponse} from '@/types/operational-readiness';

/**
 * Operational readiness 只读 API 客户端。
 *
 * Why:
 * GateM-6C 只消费 GateM-6B safe summary，用于展示 disabled capability / startup boundary。
 * 该客户端只封装 GET，不提供任何 mutation 方法。
 */
export const operationalReadinessApi = {
    async getReadiness(): Promise<OperationalReadinessResponse> {
        const {data} = await apiClient.get<OperationalReadinessResponse>('/runtime/operational-readiness');
        return data;
    },
};
