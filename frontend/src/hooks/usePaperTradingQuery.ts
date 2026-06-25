import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {paperTradingApi} from '@/api/paper-trading';
import {paperTradingQueryKeys} from '@/api/query-keys';
import type {EmergencyStopRequest, PaperRunAlertAckRequest, PaperRunAlertCreateRequest, PaperRunDailyReportGenerateRequest, PaperRunRecoverRequest, PaperRunRetryFailedStepRequest, PaperRunScheduleCreateRequest, PaperRunScheduleStatusUpdateRequest, PaperRunStabilityCheckGenerateRequest, PaperTradingRunCreateRequest} from '@/types/paper-trading';

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

export function usePaperRunSummaryQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.summary(paperRunId ?? ''),
        queryFn: () => paperTradingApi.summary(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

/** Paper 组合看板只读聚合；首屏即加载，单请求覆盖多 run 组合表现，不在前端逐 run 扇出。 */
export function usePaperPortfolioSummaryQuery() {
    return useQuery({
        queryKey: paperTradingQueryKeys.portfolioSummary(),
        queryFn: () => paperTradingApi.portfolioSummary(),
    });
}

export function usePaperTradingOrdersQuery(paperRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: paperTradingQueryKeys.orders(paperRunId ?? ''),
        queryFn: () => paperTradingApi.orders(paperRunId ?? ''),
        enabled: Boolean(paperRunId) && enabled,
    });
}

export function usePaperTradingTradesQuery(paperRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: paperTradingQueryKeys.trades(paperRunId ?? ''),
        queryFn: () => paperTradingApi.trades(paperRunId ?? ''),
        enabled: Boolean(paperRunId) && enabled,
    });
}

export function usePaperTradingPositionsQuery(paperRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: paperTradingQueryKeys.positions(paperRunId ?? ''),
        queryFn: () => paperTradingApi.positions(paperRunId ?? ''),
        enabled: Boolean(paperRunId) && enabled,
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

export function usePaperTradingRiskResultsQuery(paperRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: paperTradingQueryKeys.riskResults(paperRunId ?? ''),
        queryFn: () => paperTradingApi.riskResults(paperRunId ?? ''),
        enabled: Boolean(paperRunId) && enabled,
    });
}

export function usePaperTradingEquityCurveQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.equityCurve(paperRunId ?? ''),
        queryFn: () => paperTradingApi.equityCurve(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperTradingPositionCurveQuery(paperRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: paperTradingQueryKeys.positionCurve(paperRunId ?? ''),
        queryFn: () => paperTradingApi.positionCurve(paperRunId ?? ''),
        enabled: Boolean(paperRunId) && enabled,
    });
}

export function usePaperTradingReplayQuery(paperRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: paperTradingQueryKeys.replay(paperRunId ?? ''),
        queryFn: () => paperTradingApi.replay(paperRunId ?? ''),
        enabled: Boolean(paperRunId) && enabled,
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

export function usePaperSchedulesQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.schedules(paperRunId ?? ''),
        queryFn: () => paperTradingApi.listSchedules({paperRunId: paperRunId ?? ''}),
        enabled: Boolean(paperRunId),
    });
}

export function usePaperFiresQuery(scheduleId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.fires(scheduleId ?? ''),
        queryFn: () => paperTradingApi.listFires(scheduleId ?? ''),
        enabled: Boolean(scheduleId),
    });
}

export function usePaperHeartbeatsQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.heartbeats(paperRunId ?? ''),
        queryFn: () => paperTradingApi.listHeartbeats(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function useCreateScheduleMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (request: PaperRunScheduleCreateRequest) => paperTradingApi.createSchedule(request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useUpdateScheduleStatusMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({scheduleId, request}: {scheduleId: string; request: PaperRunScheduleStatusUpdateRequest}) =>
            paperTradingApi.updateScheduleStatus(scheduleId, request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useRunScheduleOnceMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (scheduleId: string) => paperTradingApi.runScheduleOnce(scheduleId),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useRunHeartbeatOnceMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (paperRunId: string) => paperTradingApi.runHeartbeatOnce(paperRunId),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function usePaperDailyReportsQuery(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.dailyReports(paperRunId ?? ''),
        queryFn: () => paperTradingApi.listDailyReports(paperRunId ?? ''),
        enabled: Boolean(paperRunId),
    });
}

export function useGenerateDailyReportMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId, request}: {paperRunId: string; request: PaperRunDailyReportGenerateRequest}) =>
            paperTradingApi.generateDailyReport(paperRunId, request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function usePaperAlertsQuery(paperRunId: string | null, status?: string, severity?: string) {
    return useQuery({
        queryKey: paperTradingQueryKeys.alerts(paperRunId ?? '', status, severity),
        queryFn: () => paperTradingApi.listAlerts(paperRunId ?? '', {status, severity}),
        enabled: Boolean(paperRunId),
    });
}

export function useCreateAlertMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId, request}: {paperRunId: string; request: PaperRunAlertCreateRequest}) =>
            paperTradingApi.createAlert(paperRunId, request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useAckAlertMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId, alertId, request}: {paperRunId: string; alertId: string; request?: PaperRunAlertAckRequest}) =>
            paperTradingApi.ackAlert(paperRunId, alertId, request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useResolveAlertMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId, alertId}: {paperRunId: string; alertId: string}) =>
            paperTradingApi.resolveAlert(paperRunId, alertId),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function usePaperRecoveryEventsQuery(paperRunId: string | null, recoveryType?: string, status?: string) {
    return useQuery({
        queryKey: paperTradingQueryKeys.recoveryEvents(paperRunId ?? '', recoveryType, status),
        queryFn: () => paperTradingApi.listRecoveryEvents(paperRunId ?? '', {recoveryType, status}),
        enabled: Boolean(paperRunId),
    });
}

export function useRecoverMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId, request}: {paperRunId: string; request?: PaperRunRecoverRequest}) =>
            paperTradingApi.recover(paperRunId, request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useRetryFailedStepMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId, request}: {paperRunId: string; request?: PaperRunRetryFailedStepRequest}) =>
            paperTradingApi.retryFailedStep(paperRunId, request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function usePaperStabilityChecksQuery(paperRunId: string | null, status?: string) {
    return useQuery({
        queryKey: paperTradingQueryKeys.stabilityChecks(paperRunId ?? '', status),
        queryFn: () => paperTradingApi.listStabilityChecks(paperRunId ?? '', {status}),
        enabled: Boolean(paperRunId),
    });
}

export function useGenerateStabilityCheckMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId, request}: {paperRunId: string; request: PaperRunStabilityCheckGenerateRequest}) =>
            paperTradingApi.generateStabilityCheck(paperRunId, request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}

export function useRunMonitorOnceMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({paperRunId}: {paperRunId: string}) => paperTradingApi.runMonitorOnce(paperRunId),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
        },
    });
}
