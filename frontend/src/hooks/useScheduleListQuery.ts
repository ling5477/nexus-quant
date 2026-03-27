import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {scheduleQueryKeys} from '@/api/query-keys';
import {schedulesApi} from '@/api/schedules';
import type {StrategyScheduleStatusUpdateRequest} from '@/types/schedules';

export function useScheduleListQuery(strategyId: string, searchVersion: number) {
    return useQuery({
        queryKey: scheduleQueryKeys.list(strategyId, searchVersion),
        queryFn: () => schedulesApi.list(strategyId),
        enabled: Boolean(strategyId) && searchVersion > 0,
    });
}

export function useScheduleDetailQuery(scheduleId: string | null) {
    return useQuery({
        queryKey: scheduleQueryKeys.detail(scheduleId ?? ''),
        queryFn: () => schedulesApi.detail(scheduleId ?? ''),
        enabled: Boolean(scheduleId),
    });
}

export function useUpdateScheduleStatusMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({scheduleId, request}: { scheduleId: string; request: StrategyScheduleStatusUpdateRequest }) => (
            schedulesApi.updateStatus(scheduleId, request)
        ),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({queryKey: scheduleQueryKeys.all});
            queryClient.invalidateQueries({queryKey: scheduleQueryKeys.detail(variables.scheduleId)});
        },
    });
}
