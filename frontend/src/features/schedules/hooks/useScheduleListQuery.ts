import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {scheduleQueryKeys} from '@/api/query-keys';
import {schedulesApi} from '@/features/schedules/api/schedules';
import type {
    StrategySchedulePageRequest,
    StrategyScheduleStatusUpdateRequest,
} from '@/features/schedules/types/schedules';
import {useAuthStore} from '@/store/auth-store';

/** 查询身份包含主体和完整窗口，重置或离开详情时立即取消并释放旧读取。 */
export function useScheduleListQuery(request: StrategySchedulePageRequest, revision: number) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: scheduleQueryKeys.page(owner, request, revision),
        queryFn: ({signal}) => schedulesApi.page(request, signal),
        enabled: owner !== null && Boolean(request.strategyId) && revision > 0,
        gcTime: 0,
    });
}
export function useScheduleDetailQuery(scheduleId: string | null) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: scheduleQueryKeys.detail(owner, scheduleId ?? ''),
        queryFn: ({signal}) => schedulesApi.detail(scheduleId ?? '', signal),
        enabled: owner !== null && Boolean(scheduleId),
        gcTime: 0,
    });
}
/** 既有目标状态命令不做乐观成功或自动重试；回执不明时由页面显式重读当前状态。 */
export function useUpdateScheduleStatusMutation() {
    const cache = useQueryClient();
    return useMutation({
        mutationFn: ({
            scheduleId,
            request,
        }: {
            scheduleId: string;
            request: StrategyScheduleStatusUpdateRequest;
        }) => schedulesApi.updateStatus(scheduleId, request),
        retry: false,
        onSuccess: () => {
            void cache.invalidateQueries({queryKey: scheduleQueryKeys.all});
        },
    });
}
