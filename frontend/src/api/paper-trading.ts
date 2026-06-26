import {apiClient} from '@/api/client';
import type {
    EmergencyStopEventItem,
    EmergencyStopRequest,
    EquityCurveSnapshotItem,
    PaperRiskCheckResultItem,
    PaperRunAlertAckRequest,
    PaperRunAlertCreateRequest,
    PaperRunAlertItem,
    PaperRunDailyReportGenerateRequest,
    PaperRunDailyReportItem,
    PaperRunHeartbeatItem,
    PaperRunMonitorRunOnceResponse,
    PaperRunRecoverRequest,
    PaperRunRecoveryEventItem,
    PaperRunRetryFailedStepRequest,
    PaperRunScheduleCreateRequest,
    PaperRunScheduleFireItem,
    PaperRunScheduleItem,
    PaperRunScheduleStatusUpdateRequest,
    PaperRunStabilityCheckGenerateRequest,
    PaperAutoReviewsResponse,
    PaperExecutionDiagnosticsResponse,
    PaperPortfolioSummaryResponse,
    PaperStrategyEvaluationsResponse,
    PaperRunStabilityCheckItem,
    PaperRunSummaryResponse,
    PaperTradingOrderItem,
    PaperTradingPositionItem,
    PaperTradingRunCreateRequest,
    PaperTradingRunItem,
    PaperTradingTradeItem,
    PositionCurveSnapshotItem,
    TradeReplayRecordItem,
} from '@/types/paper-trading';

export interface PaperTradingListRequest {
    publishId?: string;
    status?: string;
}

export const paperTradingApi = {
    async list(request: PaperTradingListRequest): Promise<PaperTradingRunItem[]> {
        const {data} = await apiClient.get<PaperTradingRunItem[]>('/paper-trading/runs', {
            params: request,
        });
        return data;
    },
    async detail(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.get<PaperTradingRunItem>(`/paper-trading/runs/${paperRunId}`);
        return data;
    },
    async summary(paperRunId: string): Promise<PaperRunSummaryResponse> {
        const {data} = await apiClient.get<PaperRunSummaryResponse>(`/paper-trading/runs/${paperRunId}/summary`);
        return data;
    },
    async portfolioSummary(): Promise<PaperPortfolioSummaryResponse> {
        const {data} = await apiClient.get<PaperPortfolioSummaryResponse>('/paper-trading/portfolio/summary');
        return data;
    },
    async executionDiagnostics(): Promise<PaperExecutionDiagnosticsResponse> {
        const {data} = await apiClient.get<PaperExecutionDiagnosticsResponse>('/paper-trading/execution-diagnostics');
        return data;
    },
    async strategyEvaluations(): Promise<PaperStrategyEvaluationsResponse> {
        const {data} = await apiClient.get<PaperStrategyEvaluationsResponse>('/paper-trading/strategy-evaluations');
        return data;
    },
    async autoReviews(): Promise<PaperAutoReviewsResponse> {
        const {data} = await apiClient.get<PaperAutoReviewsResponse>('/paper-trading/auto-reviews');
        return data;
    },
    async create(request: PaperTradingRunCreateRequest): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.post<PaperTradingRunItem>('/paper-trading/runs', request);
        return data;
    },
    async start(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.post<PaperTradingRunItem>(`/paper-trading/runs/${paperRunId}/start`);
        return data;
    },
    async stop(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.post<PaperTradingRunItem>(`/paper-trading/runs/${paperRunId}/stop`);
        return data;
    },
    async orders(paperRunId: string): Promise<PaperTradingOrderItem[]> {
        const {data} = await apiClient.get<PaperTradingOrderItem[]>(`/paper-trading/runs/${paperRunId}/orders`);
        return data;
    },
    async trades(paperRunId: string): Promise<PaperTradingTradeItem[]> {
        const {data} = await apiClient.get<PaperTradingTradeItem[]>(`/paper-trading/runs/${paperRunId}/trades`);
        return data;
    },
    async positions(paperRunId: string): Promise<PaperTradingPositionItem[]> {
        const {data} = await apiClient.get<PaperTradingPositionItem[]>(`/paper-trading/runs/${paperRunId}/positions`);
        return data;
    },
    async riskResults(paperRunId: string): Promise<PaperRiskCheckResultItem[]> {
        const {data} = await apiClient.get<PaperRiskCheckResultItem[]>(`/paper-trading/runs/${paperRunId}/risk-results`);
        return data;
    },
    async runRiskOnce(paperRunId: string): Promise<PaperRiskCheckResultItem> {
        const {data} = await apiClient.post<PaperRiskCheckResultItem>(`/paper-trading/runs/${paperRunId}/risk-results/run-once`);
        return data;
    },
    async equityCurve(paperRunId: string): Promise<EquityCurveSnapshotItem[]> {
        const {data} = await apiClient.get<EquityCurveSnapshotItem[]>(`/paper-trading/runs/${paperRunId}/equity-curve`);
        return data;
    },
    async positionCurve(paperRunId: string): Promise<PositionCurveSnapshotItem[]> {
        const {data} = await apiClient.get<PositionCurveSnapshotItem[]>(`/paper-trading/runs/${paperRunId}/position-curve`);
        return data;
    },
    async replay(paperRunId: string): Promise<TradeReplayRecordItem[]> {
        const {data} = await apiClient.get<TradeReplayRecordItem[]>(`/paper-trading/runs/${paperRunId}/replay`);
        return data;
    },
    async emergencyStop(paperRunId: string, request: EmergencyStopRequest): Promise<EmergencyStopEventItem> {
        const {data} = await apiClient.post<EmergencyStopEventItem>(`/paper-trading/runs/${paperRunId}/emergency-stop`, request);
        return data;
    },
    async emergencyStops(paperRunId: string): Promise<EmergencyStopEventItem[]> {
        const {data} = await apiClient.get<EmergencyStopEventItem[]>(`/paper-trading/runs/${paperRunId}/emergency-stops`);
        return data;
    },
    async listSchedules(params: {paperRunId?: string; status?: string}): Promise<PaperRunScheduleItem[]> {
        const {data} = await apiClient.get<PaperRunScheduleItem[]>('/paper-trading/schedules', {params});
        return data;
    },
    async createSchedule(request: PaperRunScheduleCreateRequest): Promise<PaperRunScheduleItem> {
        const {data} = await apiClient.post<PaperRunScheduleItem>('/paper-trading/schedules', request);
        return data;
    },
    async scheduleDetail(scheduleId: string): Promise<PaperRunScheduleItem> {
        const {data} = await apiClient.get<PaperRunScheduleItem>(`/paper-trading/schedules/${scheduleId}`);
        return data;
    },
    async updateScheduleStatus(scheduleId: string, request: PaperRunScheduleStatusUpdateRequest): Promise<PaperRunScheduleItem> {
        const {data} = await apiClient.patch<PaperRunScheduleItem>(`/paper-trading/schedules/${scheduleId}/status`, request);
        return data;
    },
    async runScheduleOnce(scheduleId: string): Promise<PaperRunScheduleFireItem> {
        const {data} = await apiClient.post<PaperRunScheduleFireItem>(`/paper-trading/schedules/${scheduleId}/run-once`);
        return data;
    },
    async listFires(scheduleId: string): Promise<PaperRunScheduleFireItem[]> {
        const {data} = await apiClient.get<PaperRunScheduleFireItem[]>(`/paper-trading/schedules/${scheduleId}/fires`);
        return data;
    },
    async listHeartbeats(paperRunId: string): Promise<PaperRunHeartbeatItem[]> {
        const {data} = await apiClient.get<PaperRunHeartbeatItem[]>(`/paper-trading/runs/${paperRunId}/heartbeats`);
        return data;
    },
    async runHeartbeatOnce(paperRunId: string): Promise<PaperRunHeartbeatItem> {
        const {data} = await apiClient.post<PaperRunHeartbeatItem>(`/paper-trading/runs/${paperRunId}/heartbeats/run-once`);
        return data;
    },
    async listDailyReports(paperRunId: string): Promise<PaperRunDailyReportItem[]> {
        const {data} = await apiClient.get<PaperRunDailyReportItem[]>(`/paper-trading/runs/${paperRunId}/daily-reports`);
        return data;
    },
    async generateDailyReport(paperRunId: string, request: PaperRunDailyReportGenerateRequest): Promise<PaperRunDailyReportItem> {
        const {data} = await apiClient.post<PaperRunDailyReportItem>(`/paper-trading/runs/${paperRunId}/daily-reports/generate`, request);
        return data;
    },
    async listAlerts(paperRunId: string, params?: {status?: string; severity?: string}): Promise<PaperRunAlertItem[]> {
        const {data} = await apiClient.get<PaperRunAlertItem[]>(`/paper-trading/runs/${paperRunId}/alerts`, {params});
        return data;
    },
    async createAlert(paperRunId: string, request: PaperRunAlertCreateRequest): Promise<PaperRunAlertItem> {
        const {data} = await apiClient.post<PaperRunAlertItem>(`/paper-trading/runs/${paperRunId}/alerts`, request);
        return data;
    },
    async ackAlert(paperRunId: string, alertId: string, request?: PaperRunAlertAckRequest): Promise<PaperRunAlertItem> {
        const {data} = await apiClient.patch<PaperRunAlertItem>(`/paper-trading/runs/${paperRunId}/alerts/${alertId}/ack`, request ?? {});
        return data;
    },
    async resolveAlert(paperRunId: string, alertId: string): Promise<PaperRunAlertItem> {
        const {data} = await apiClient.patch<PaperRunAlertItem>(`/paper-trading/runs/${paperRunId}/alerts/${alertId}/resolve`, {});
        return data;
    },
    async listRecoveryEvents(paperRunId: string, params?: {recoveryType?: string; status?: string}): Promise<PaperRunRecoveryEventItem[]> {
        const {data} = await apiClient.get<PaperRunRecoveryEventItem[]>(`/paper-trading/runs/${paperRunId}/recovery-events`, {params});
        return data;
    },
    async recover(paperRunId: string, request?: PaperRunRecoverRequest): Promise<PaperRunRecoveryEventItem> {
        const {data} = await apiClient.post<PaperRunRecoveryEventItem>(`/paper-trading/runs/${paperRunId}/recover`, request ?? {});
        return data;
    },
    async retryFailedStep(paperRunId: string, request?: PaperRunRetryFailedStepRequest): Promise<PaperRunRecoveryEventItem> {
        const {data} = await apiClient.post<PaperRunRecoveryEventItem>(`/paper-trading/runs/${paperRunId}/retry-failed-step`, request ?? {});
        return data;
    },
    async listStabilityChecks(paperRunId: string, params?: {status?: string}): Promise<PaperRunStabilityCheckItem[]> {
        const {data} = await apiClient.get<PaperRunStabilityCheckItem[]>(`/paper-trading/runs/${paperRunId}/stability-checks`, {params});
        return data;
    },
    async generateStabilityCheck(paperRunId: string, request: PaperRunStabilityCheckGenerateRequest): Promise<PaperRunStabilityCheckItem> {
        const {data} = await apiClient.post<PaperRunStabilityCheckItem>(`/paper-trading/runs/${paperRunId}/stability-checks/generate`, request);
        return data;
    },
    async runMonitorOnce(paperRunId: string): Promise<PaperRunMonitorRunOnceResponse> {
        const {data} = await apiClient.post<PaperRunMonitorRunOnceResponse>(`/paper-trading/runs/${paperRunId}/monitor/run-once`, {});
        return data;
    },
};
