import {useQuery} from '@tanstack/react-query';

import {runQueryKeys} from '@/api/query-keys';
import {runsApi} from '@/api/runs';

interface UseRunListQueryRequest {
    strategyId?: string;
    scheduleId?: string;
}

export function useRunListQuery(request: UseRunListQueryRequest, searchVersion: number) {
    const hasQuery = Boolean(request.strategyId || request.scheduleId);

    return useQuery({
        queryKey: runQueryKeys.list(request, searchVersion),
        queryFn: () => runsApi.list(request),
        enabled: hasQuery && searchVersion > 0,
    });
}

export function useRunDetailQuery(runId: string | null) {
    return useQuery({
        queryKey: runQueryKeys.detail(runId ?? ''),
        queryFn: () => runsApi.detail(runId ?? ''),
        enabled: Boolean(runId),
    });
}
