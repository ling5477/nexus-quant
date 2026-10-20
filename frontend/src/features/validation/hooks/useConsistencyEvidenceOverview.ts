import {useQuery} from '@tanstack/react-query';

import {consistencyEvidenceApi} from '@/features/validation/api/consistency-evidence';
import {consistencyEvidenceQueryKeys} from '@/api/query-keys';

/**
 * 一致性证据只读查询 hook.
 *
 * Why:
 * Consistency evidence overview 是诊断证据面板的只读事实源。关闭 retry 可以让 error 状态立即显式展示，
 * 避免自动重试把后端不可用误表现为仍可继续复核或存在可执行能力。
 */
export function useConsistencyEvidenceOverview() {
    return useQuery({
        queryKey: consistencyEvidenceQueryKeys.overview(),
        queryFn: () => consistencyEvidenceApi.getConsistencyEvidenceOverview(),
        retry: false,
    });
}
