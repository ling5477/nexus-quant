import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {strategyQueryKeys} from '@/api/query-keys';
import {strategiesApi} from '@/api/strategies';
import type {StrategyStatusUpdateRequest} from '@/types/strategies';

export function useStrategyListQuery(searchVersion: number) {
    return useQuery({
        queryKey: strategyQueryKeys.list(searchVersion),
        queryFn: strategiesApi.list,
        enabled: searchVersion > 0,
    });
}

export function useStrategyDetailQuery(strategyCode: string | null) {
    return useQuery({
        queryKey: strategyQueryKeys.detail(strategyCode ?? ''),
        queryFn: () => strategiesApi.detail(strategyCode ?? ''),
        enabled: Boolean(strategyCode),
    });
}

export function useUpdateStrategyStatusMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({strategyCode, request}: { strategyCode: string; request: StrategyStatusUpdateRequest }) => (
            strategiesApi.updateStatus(strategyCode, request)
        ),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({queryKey: strategyQueryKeys.all});
            queryClient.invalidateQueries({queryKey: strategyQueryKeys.detail(variables.strategyCode)});
        },
    });
}
