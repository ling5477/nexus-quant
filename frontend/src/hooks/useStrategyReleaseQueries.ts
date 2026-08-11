import {useQuery} from '@tanstack/react-query';

import {strategyReleaseQueryKeys} from '@/api/query-keys';
import {getStrategyReleaseAdmissionPreview} from '@/api/strategy-releases';

/** 按 publishRecordId 隔离缓存的只读 admission preview query。 */
export function useStrategyReleaseAdmissionPreview(publishRecordId: string | null) {
    return useQuery({
        queryKey: strategyReleaseQueryKeys.admissionPreview(publishRecordId),
        queryFn: () => getStrategyReleaseAdmissionPreview(publishRecordId as string),
        enabled: Boolean(publishRecordId),
        retry: false,
    });
}
