import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {evaluationsApi} from '@/api/evaluations';
import {evaluationsQueryKeys} from '@/api/query-keys';
import type {EvaluationPageRequest} from '@/types/evaluations';
import {useAuthStore} from '@/store/auth-store';

export function useEvaluationsListQuery(request: EvaluationPageRequest, searchVersion: number) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: evaluationsQueryKeys.page(owner, request, searchVersion),
        queryFn: ({signal}) => evaluationsApi.page(request, signal),
        enabled: searchVersion > 0 && owner !== null,
        // 不跨主体/筛选窗口保留结果；旧读取通过signal取消，离页释放列表缓存。
        gcTime: 0,
    });
}

export function useEvaluationDetailQuery(evaluationId: string | null) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: [...evaluationsQueryKeys.detail(evaluationId ?? ''), owner],
        queryFn: () => evaluationsApi.detail(evaluationId ?? ''),
        enabled: owner !== null && Boolean(evaluationId),
        gcTime: 0,
    });
}

export function useEvaluateMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({runId, requestId}: {runId: string; requestId: string}) =>
            evaluationsApi.evaluate(runId, requestId),
        onSuccess: () => {
            // 响应不能按回调时的新主体写入详情缓存；由当前主体重新读取正式结果。
            queryClient.invalidateQueries({queryKey: [...evaluationsQueryKeys.all, 'list']});
            queryClient.invalidateQueries({queryKey: [...evaluationsQueryKeys.all, 'page']});
        },
    });
}

export function useEvaluationRequestQuery(requestId: string | null) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: ['evaluations', 'original-request', owner, requestId],
        queryFn: () => evaluationsApi.requestReport(requestId!),
        enabled: Boolean(owner && requestId),
        retry: false,
        gcTime: 0,
    });
}
