import {apiClient} from '@/api/client';
import type {StrategyReleaseAdmissionPreviewResponse} from '@/types/strategy-releases';

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
