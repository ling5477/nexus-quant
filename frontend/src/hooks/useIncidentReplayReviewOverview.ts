import {useQuery} from '@tanstack/react-query';

import {incidentReplayReviewApi} from '@/api/incident-replay-review';
import {incidentReplayReviewQueryKeys} from '@/api/query-keys';

/**
 * GateT-3 Incident / Replay Review read-only query hook.
 *
 * Why:
 * Review overview 是人工诊断复核面板的只读事实源。关闭 retry 可以让 error 状态立即显式展示，
 * 避免自动重试把后端不可用误表现为仍可继续复核、已处置或存在可执行能力。
 */
export function useIncidentReplayReviewOverview() {
    return useQuery({
        queryKey: incidentReplayReviewQueryKeys.overview(),
        queryFn: () => incidentReplayReviewApi.getIncidentReplayReviewOverview(),
        retry: false,
    });
}
