import {apiClient} from '@/api/client';
import type {ShadowValidationWorkflowOverviewResponse} from '@/types/shadow-validation-workflow';

/**
 * 读取 GateT-1 Shadow Validation Workflow overview。
 *
 * Why:
 * 该 client 只发起 GET 请求，复用统一 Axios 实例，不提供 review / acknowledge / approve / reject、
 * start / stop / execute / trade 或任何 private exchange 请求，避免前端误形成写侧能力。
 */
export async function getShadowValidationWorkflowOverview(): Promise<ShadowValidationWorkflowOverviewResponse> {
    const {data} = await apiClient.get<ShadowValidationWorkflowOverviewResponse>(
        '/shadow-validation/workflow/overview',
    );
    return data;
}

export const shadowValidationWorkflowApi = {
    getShadowValidationWorkflowOverview,
};
