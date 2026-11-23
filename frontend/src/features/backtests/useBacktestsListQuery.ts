import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {backtestsApi} from '@/features/backtests/backtestsApi';
import {backtestsQueryKeys} from '@/api/query-keys';
import type {
    BacktestConfigCreateRequest,
    BacktestDatasetBindingRequest,
    BacktestStrategyVersionBindingRequest,
    BacktestPageRequest,
} from '@/features/backtests/backtestsTypes';
import {useAuthStore} from '@/store/auth-store';

export function useBacktestsListQuery(request: BacktestPageRequest, searchVersion: number) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: backtestsQueryKeys.page(owner, request, searchVersion),
        queryFn: ({signal}) => backtestsApi.page(request, signal),
        enabled: searchVersion > 0 && owner !== null,
        // 私有费用来源的读取不能跨用户或筛选窗口复用；离页取消并释放列表缓存。
        gcTime: 0,
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

/** 归档后仍按原ID读取冻结历史；未知写响应先重读，不把它当作新建动作。 */
export function useArchiveBacktestMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({configId, reason}: {configId: string; reason?: string}) =>
            backtestsApi.archive(configId, reason),
        // 缓存重读不能延长确认框的提交Promise；失败后必须立即允许取消或原对象重试。
        onSettled: () => {
            void queryClient.invalidateQueries({queryKey: backtestsQueryKeys.all});
        },
    });
}

export function useBindBacktestDatasetMutation(configId: string | null) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (request: BacktestDatasetBindingRequest) =>
            backtestsApi.bindDataset(configId ?? '', request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: backtestsQueryKeys.all});
            if (configId) {
                queryClient.invalidateQueries({queryKey: backtestsQueryKeys.detail(configId)});
            }
        },
    });
}

export function useBindBacktestStrategyVersionMutation(configId: string | null) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (request: BacktestStrategyVersionBindingRequest) =>
            backtestsApi.bindStrategyVersion(configId ?? '', request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: backtestsQueryKeys.all});
            if (configId) {
                queryClient.invalidateQueries({queryKey: backtestsQueryKeys.detail(configId)});
            }
        },
    });
}

export function useCreateBacktestRunMutation() {
    return useMutation({
        mutationFn: (backtestConfigId: string) => backtestsApi.createRun({backtestConfigId}),
    });
}

export function useBacktestRunDetailQuery(runId: string | null) {
    return useQuery({
        queryKey: [...backtestsQueryKeys.all, 'run-detail', runId ?? ''],
        queryFn: () => backtestsApi.getRun(runId ?? ''),
        enabled: Boolean(runId),
    });
}
