import {apiClient} from '@/api/client';
import type {ValidationOperationsRuntimeEvidenceOverviewResponse} from '@/types/validation-operations-runtime-evidence';

/**
 * 读取 Validation Operations Runtime Evidence Overview。
 *
 * Why: client 只发起一个 aggregate GET；不分别触发五个来源的 refresh，不提供 mutation、scheduler、交易、
 * credential、upload/import 或任何 private exchange 请求。
 */
export async function getValidationOperationsRuntimeEvidenceOverview(): Promise<ValidationOperationsRuntimeEvidenceOverviewResponse> {
    const {data} = await apiClient.get<ValidationOperationsRuntimeEvidenceOverviewResponse>(
        '/validation-operations/runtime-evidence/overview',
    );
    return data;
}

export const validationOperationsRuntimeEvidenceApi = {
    getValidationOperationsRuntimeEvidenceOverview,
};
