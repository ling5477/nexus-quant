import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {paperTradingApi} from '@/api/paper-trading';
import {paperTradingQueryKeys} from '@/api/query-keys';
import type {PaperTradingRunCreateRequest} from '@/types/paper-trading';

interface UsePaperTradingListRequest {
    publishId?: string;
    status?: string;
}

export function usePaperTradingListQuery(request: UsePaperTradingListRequest, searchVersion: number) {
    return useQuery({
        queryKey: paperTradingQueryKeys.list(request, searchVersion),
        queryFn: () => paperTradingApi.list(request),
        enabled: searchVersion > 0,
    });
}

export function usePaperTradingDetailQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.detail(paperRunId ?? ''),
        queryFn: () => paperTradingApi.detail(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperTradingOrdersQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.orders(paperRunId ?? ''),
        queryFn: () => paperTradingApi.orders(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperTradingTradesQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.trades(paperRunId ?? ''),
        queryFn: () => paperTradingApi.trades(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperTradingPositionsQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.positions(paperRunId ?? ''),
        queryFn: () => paperTradingApi.positions(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function useCreatePaperTradingRunMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (request: PaperTradingRunCreateRequest) => paperTradingApi.create(request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useStartPaperTradingRunMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (paperRunId: string) => paperTradingApi.start(paperRunId),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useStopPaperTradingRunMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (paperRunId: string) => paperTradingApi.stop(paperRunId),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}
