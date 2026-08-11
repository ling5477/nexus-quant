import {useMutation, useQuery} from '@tanstack/react-query';

import {strategyReleaseQueryKeys} from '@/api/query-keys';
import {
    getStrategyReleaseAdmissionPreview,
    materializeStrategyReleaseShadowRun,
} from '@/api/strategy-releases';

/** 按 publishRecordId 隔离缓存的只读 admission preview query。 */
export function useStrategyReleaseAdmissionPreview(publishRecordId: string | null) {
    return useQuery({
        queryKey: strategyReleaseQueryKeys.admissionPreview(publishRecordId),
        queryFn: () => getStrategyReleaseAdmissionPreview(publishRecordId as string),
        enabled: Boolean(publishRecordId),
        retry: false,
    });
}

/** 写请求禁用自动 retry；网络重试必须由用户明确复用同一 Idempotency-Key。 */
export function useStrategyReleaseShadowRunMaterialization() {
    return useMutation({
        mutationFn: ({publishRecordId, idempotencyKey}: {
            publishRecordId: string;
            idempotencyKey: string;
        }) => materializeStrategyReleaseShadowRun(publishRecordId, idempotencyKey),
        retry: false,
    });
}
