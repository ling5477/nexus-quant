import {apiClient} from '@/api/client';
import type {
    StrategyReleaseAdmissionPreviewResponse,
    StrategyReleaseShadowRunMaterializationResponse,
} from '@/types/strategy-releases';

/** 只提交 publishRecordId 的 Release admission GET client。 */
export async function getStrategyReleaseAdmissionPreview(
    publishRecordId: string,
): Promise<StrategyReleaseAdmissionPreviewResponse> {
    const encodedPublishRecordId = encodeURIComponent(publishRecordId.trim());
    const {data} = await apiClient.get<StrategyReleaseAdmissionPreviewResponse>(
        `/strategy-releases/${encodedPublishRecordId}/shadow-admission-preview`,
    );
    return data;
}

/** 使用稳定 command identity 创建一个未启动的 CREATED Shadow Run。 */
export async function materializeStrategyReleaseShadowRun(
    publishRecordId: string,
    idempotencyKey: string,
): Promise<StrategyReleaseShadowRunMaterializationResponse> {
    const encodedPublishRecordId = encodeURIComponent(publishRecordId.trim());
    const {data} = await apiClient.post<StrategyReleaseShadowRunMaterializationResponse>(
        `/strategy-releases/${encodedPublishRecordId}/shadow-runs`,
        undefined,
        {headers: {'Idempotency-Key': idempotencyKey}},
    );
    return data;
}
