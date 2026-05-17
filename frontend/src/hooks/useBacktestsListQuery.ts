import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {backtestsApi} from '@/api/backtests';
import {backtestsQueryKeys} from '@/api/query-keys';
import type {BacktestConfigCreateRequest, BacktestDatasetBindingRequest} from '@/types/backtests';

export function useBacktestsListQuery(researchConfigId: string, searchVersion: number) {
    return useQuery({
        queryKey: backtestsQueryKeys.list(researchConfigId, searchVersion),
        queryFn: () => backtestsApi.list(researchConfigId || undefined),
        enabled: searchVersion > 0,
    });
}

export function useBacktestDetailQuery(configId: string | null) {
    return useQuery({
        queryKey: backtestsQueryKeys.detail(configId ?? ''),
        queryFn: () => backtestsApi.detail(configId ?? ''),
        enabled: Boolean(configId),
    });
}

export function useCreateBacktestMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (request: BacktestConfigCreateRequest) => backtestsApi.create(request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: backtestsQueryKeys.all});
        },
    });
}

export function useBindBacktestDatasetMutation(configId: string | null) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (request: BacktestDatasetBindingRequest) => backtestsApi.bindDataset(configId ?? '', request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: backtestsQueryKeys.all});
            if (configId) {
                queryClient.invalidateQueries({queryKey: backtestsQueryKeys.detail(configId)});
            }
        },
    });
}
