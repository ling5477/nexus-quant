import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {publishesApi} from '@/features/publishes/api/publishes';
import {publishesQueryKeys} from '@/api/query-keys';
import type {BacktestPublishRequest} from '@/features/publishes/types/publishes';
import type {PublishPageRequest} from '@/features/publishes/types/publishes';
import {useAuthStore} from '@/store/auth-store';

export function usePublishesListQuery(request: PublishPageRequest, searchVersion: number) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: publishesQueryKeys.page(owner, request, searchVersion),
        queryFn: ({signal}) => publishesApi.page(request, signal),
        enabled: searchVersion > 0 && owner !== null,
        // 发布读取与原所有者范围一致，迟到旧页不能成为新筛选或另一用户的结果。
        gcTime: 0,
    });
}

export function usePublishDetailQuery(runId: string | null) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: [...publishesQueryKeys.detail(runId ?? ''), owner],
        queryFn: () => publishesApi.detail(runId ?? ''),
        enabled: owner !== null && Boolean(runId),
        gcTime: 0,
    });
}

export function usePublishMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({runId, request}: {runId: string; request?: BacktestPublishRequest}) =>
            publishesApi.publish(runId, request),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({queryKey: publishesQueryKeys.all});
            queryClient.invalidateQueries({queryKey: publishesQueryKeys.detail(variables.runId)});
        },
    });
}
