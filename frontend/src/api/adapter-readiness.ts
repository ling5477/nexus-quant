import {apiClient} from '@/api/client';
import type {AdapterReadinessResponse} from '@/types/adapter-readiness';

/**
 * Adapter readiness 只读 API 客户端。
 *
 * Why:
 * 只读消费后端 GateM-5A `GET /api/adapters/readiness`，用于前端展示各 venue × capability 当前 readiness。
 * 不触发任何真实交易、下单、撤单、行情订阅或凭证读取——后端该端点本身即 fail-closed 只读。
 */
export const adapterReadinessApi = {
    async getReadiness(): Promise<AdapterReadinessResponse> {
        const {data} = await apiClient.get<AdapterReadinessResponse>('/adapters/readiness');
        return data;
    },
};
