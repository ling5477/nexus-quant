import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {publishesApi} from '@/api/publishes';
import {publishesQueryKeys} from '@/api/query-keys';
import type {BacktestPublishRequest} from '@/types/publishes';

interface UsePublishesListQueryRequest {
    researchConfigId?: string;
    backtestConfigId?: string;
}

export function usePublishesListQuery(request: UsePublishesListQueryRequest, searchVersion: number) {
    return useQuery({
        queryKey: publishesQueryKeys.list(request, searchVersion),
        queryFn: () => publishesApi.list(request),
        enabled: searchVersion > 0,
    });
}

export function usePublishDetailQuery(runId: string | null) {
    return useQuery({
        queryKey: publishesQueryKeys.detail(runId ?? ''),
        queryFn: () => publishesApi.detail(runId ?? ''),
        enabled: Boolean(runId),
    });
}

export function usePublishMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({runId, request}: {
            runId: string;
            request?: BacktestPublishRequest
        }) => publishesApi.publish(runId, request),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({queryKey: publishesQueryKeys.all});
            queryClient.invalidateQueries({queryKey: publishesQueryKeys.detail(variables.runId)});
        },
    });
}
