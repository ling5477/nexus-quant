import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {evaluationsApi} from '@/api/evaluations';
import {evaluationsQueryKeys} from '@/api/query-keys';

interface UseEvaluationsListQueryRequest {
    researchConfigId?: string;
    backtestConfigId?: string;
}

export function useEvaluationsListQuery(request: UseEvaluationsListQueryRequest, searchVersion: number) {
    return useQuery({
        queryKey: evaluationsQueryKeys.list(request, searchVersion),
        queryFn: () => evaluationsApi.list(request),
        enabled: searchVersion > 0,
    });
}

export function useEvaluationDetailQuery(runId: string | null) {
    return useQuery({
        queryKey: evaluationsQueryKeys.detail(runId ?? ''),
        queryFn: () => evaluationsApi.detail(runId ?? ''),
        enabled: Boolean(runId),
    });
}

export function useEvaluateMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (runId: string) => evaluationsApi.evaluate(runId),
        onSuccess: (_, runId) => {
            queryClient.invalidateQueries({queryKey: evaluationsQueryKeys.all});
            queryClient.invalidateQueries({queryKey: evaluationsQueryKeys.detail(runId)});
        },
    });
}
