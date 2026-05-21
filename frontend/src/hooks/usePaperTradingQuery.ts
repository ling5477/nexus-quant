import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {paperTradingApi} from '@/api/paper-trading';
import {paperTradingQueryKeys} from '@/api/query-keys';
import type {EmergencyStopRequest, PaperTradingRunCreateRequest} from '@/types/paper-trading';

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

export function usePaperTradingRiskResultsQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.riskResults(paperRunId ?? ''),
        queryFn: () => paperTradingApi.riskResults(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperTradingEquityCurveQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.equityCurve(paperRunId ?? ''),
        queryFn: () => paperTradingApi.equityCurve(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperTradingPositionCurveQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.positionCurve(paperRunId ?? ''),
        queryFn: () => paperTradingApi.positionCurve(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperTradingReplayQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.replay(paperRunId ?? ''),
        queryFn: () => paperTradingApi.replay(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperTradingEmergencyStopsQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.emergencyStops(paperRunId ?? ''),
        queryFn: () => paperTradingApi.emergencyStops(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function useRunRiskOnceMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (paperRunId: string) => paperTradingApi.runRiskOnce(paperRunId),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useEmergencyStopMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId, request}: {paperRunId: string; request: EmergencyStopRequest}) =>
            paperTradingApi.emergencyStop(paperRunId, request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}
