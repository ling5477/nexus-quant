import {submitResultAction} from '@/features/research-result-recovery';
import {apiClient} from '@/api/client';
import {createRecoverably} from '@/features/research/creation-operations';
import {parseExactJson} from '@/api/exact-json';
import {parseSimDecisionResponse} from '@/features/paper-trading/analysis-response';
import {parseContinuousSimStatus} from '@/features/paper-trading/continuous-state';
import {
    paperAlertOperations,
    parsePaperAlert,
} from '@/features/paper-trading/paper-alert-operations';
import type {
    EmergencyStopEventItem,
    StrategySimDecision,
    StrategySimFacts,
    ContinuousSimStatus,
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
} from '@/features/paper-trading/types/paper-trading';

interface PaperTradingListRequest {
    publishId?: string;
    status?: string;
}

export const paperTradingApi = {
    /** 撤单身份由原订单决定；客户端不自动重试，未知响应通过订单详情查询收敛。 */
    async cancelSimOrder(
        paperRunId: string,
        orderId: string,
    ): Promise<{orderId: string; status: string; idempotentHit: boolean}> {
        const {data} = await apiClient.post(
            `/paper-trading/strategy-sim/runs/${encodeURIComponent(paperRunId)}/orders/${encodeURIComponent(orderId)}/cancel`,
        );
        return data;
    },
    async refreshSimExecution(
        paperRunId: string,
        orderId: string,
    ): Promise<{
        paperRunId: string;
        orderId: string;
        strategyRunId: string;
        status: string;
        projected: boolean;
    }> {
        const {data} = await apiClient.post(
            `/paper-trading/strategy-sim/runs/${encodeURIComponent(paperRunId)}/orders/${encodeURIComponent(orderId)}/refresh-execution`,
        );
        return data;
    },
    async strategySimOrder(paperRunId: string, orderId: string): Promise<CanonicalSimOrderDetails> {
        const {data} = await apiClient.get<CanonicalSimOrderDetails>(
            `/paper-trading/strategy-sim/runs/${encodeURIComponent(paperRunId)}/orders/${encodeURIComponent(orderId)}`,
            {transformResponse: [(text: string) => parseExactJson(text, new Set(['account_id']))]},
        );
        return data;
    },
    /** 创建响应丢失后保留原UUID，必须先读取原对象，不能通过重新点击生成另一份预算。 */
    async createStrategySim(publishId: string, budget: string): Promise<PaperTradingRunItem> {
        return createRecoverably('SIM_RUN', {publishId, budget}, (receipt) =>
            paperTradingApi.detail(receipt.objectId),
        );
    },
    async continuousSimStatus(
        paperRunId: string,
        signal?: AbortSignal,
    ): Promise<ContinuousSimStatus | null> {
        const {data} = await apiClient.get<ContinuousSimStatus | null>(
            `/paper-trading/strategy-sim/runs/${encodeURIComponent(paperRunId)}/continuous`,
            {signal},
        );
        return parseContinuousSimStatus(data, paperRunId);
    },
    /** 服务端入口可用性与当前运行状态分别读取；前端构建开关不能证明后端已启用。 */
    async continuousSimCapabilities(): Promise<{commandsAvailable: boolean}> {
        const {data} = await apiClient.get<{commandsAvailable: boolean}>(
            '/paper-trading/strategy-sim/runs/continuous-capabilities',
        );
        if (typeof data?.commandsAvailable !== 'boolean')
            throw new Error('Invalid continuous capabilities');
        return data;
    },
    async advanceStrategySim(paperRunId: string): Promise<StrategySimDecision> {
        const {data} = await apiClient.post<StrategySimDecision>(
            `/paper-trading/strategy-sim/runs/${paperRunId}/advance`,
            undefined,
            {transformResponse: [(text: string) => parseSimDecisionResponse(text)]},
        );
        return data;
    },
    async strategySimDecisions(paperRunId: string): Promise<StrategySimDecision[]> {
        const {data} = await apiClient.get<StrategySimDecision[]>(
            `/paper-trading/strategy-sim/runs/${paperRunId}/decisions`,
            {transformResponse: [(text: string) => parseSimDecisionResponse(text)]},
        );
        return data;
    },
    async strategySimFacts(paperRunId: string): Promise<StrategySimFacts> {
        const {data} = await apiClient.get<StrategySimFacts>(
            `/paper-trading/strategy-sim/runs/${paperRunId}/facts`,
            {
                transformResponse: [
                    (text: string) =>
                        parseExactJson(
                            text,
                            new Set([
                                'initialBudget',
                                'cash',
                                'positionQuantity',
                                'markPrice',
                                'equity',
                                'pnl',
                                'qty',
                                'price',
                                'fee',
                                'delta',
                                'avg_price',
                            ]),
                        ),
                ],
            },
        );
        return data;
    },
    async list(request: PaperTradingListRequest): Promise<PaperTradingRunItem[]> {
        const {data} = await apiClient.get<PaperTradingRunItem[]>('/paper-trading/runs', {
            params: request,
        });
        return data;
    },
    async detail(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.get<PaperTradingRunItem>(
            `/paper-trading/runs/${paperRunId}`,
        );
        return data;
    },
    async summary(paperRunId: string): Promise<PaperRunSummaryResponse> {
        const {data} = await apiClient.get<PaperRunSummaryResponse>(
            `/paper-trading/runs/${paperRunId}/summary`,
        );
        return data;
    },
    async portfolioSummary(): Promise<PaperPortfolioSummaryResponse> {
        const {data} = await apiClient.get<PaperPortfolioSummaryResponse>(
            '/paper-trading/portfolio/summary',
        );
        return data;
    },
    async executionDiagnostics(): Promise<PaperExecutionDiagnosticsResponse> {
        const {data} = await apiClient.get<PaperExecutionDiagnosticsResponse>(
            '/paper-trading/execution-diagnostics',
        );
        return data;
    },
    async strategyEvaluations(): Promise<PaperStrategyEvaluationsResponse> {
        const {data} = await apiClient.get<PaperStrategyEvaluationsResponse>(
            '/paper-trading/strategy-evaluations',
        );
        return data;
    },
    async autoReviews(): Promise<PaperAutoReviewsResponse> {
        const {data} = await apiClient.get<PaperAutoReviewsResponse>('/paper-trading/auto-reviews');
        return data;
    },
    async create(request: PaperTradingRunCreateRequest): Promise<PaperTradingRunItem> {
        // 原Research Paper不具有独立资金；这里只绑定稳定创建请求，未知时按原回执恢复。
        return createRecoverably('PAPER_RUN', request, (receipt) =>
            paperTradingApi.detail(receipt.objectId),
        );
    },
    async start(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.post<PaperTradingRunItem>(
            `/paper-trading/runs/${paperRunId}/start`,
        );
        return data;
    },
    async stop(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.post<PaperTradingRunItem>(
            `/paper-trading/runs/${paperRunId}/stop`,
        );
        return data;
    },
    async orders(paperRunId: string): Promise<PaperTradingOrderItem[]> {
        const {data} = await apiClient.get<PaperTradingOrderItem[]>(
            `/paper-trading/runs/${paperRunId}/orders`,
        );
        return data;
    },
    async trades(paperRunId: string): Promise<PaperTradingTradeItem[]> {
        const {data} = await apiClient.get<PaperTradingTradeItem[]>(
            `/paper-trading/runs/${paperRunId}/trades`,
        );
        return data;
    },
    async positions(paperRunId: string): Promise<PaperTradingPositionItem[]> {
        const {data} = await apiClient.get<PaperTradingPositionItem[]>(
            `/paper-trading/runs/${paperRunId}/positions`,
        );
        return data;
    },
    async riskResults(paperRunId: string): Promise<PaperRiskCheckResultItem[]> {
        const {data} = await apiClient.get<PaperRiskCheckResultItem[]>(
            `/paper-trading/runs/${paperRunId}/risk-results`,
        );
        return data;
    },
    async runRiskOnce(paperRunId: string): Promise<PaperRiskCheckResultItem> {
        const {data} = await apiClient.post<PaperRiskCheckResultItem>(
            `/paper-trading/runs/${paperRunId}/risk-results/run-once`,
        );
        return data;
    },
    async equityCurve(paperRunId: string): Promise<EquityCurveSnapshotItem[]> {
        const {data} = await apiClient.get<EquityCurveSnapshotItem[]>(
            `/paper-trading/runs/${paperRunId}/equity-curve`,
        );
        return data;
    },
    async positionCurve(paperRunId: string): Promise<PositionCurveSnapshotItem[]> {
        const {data} = await apiClient.get<PositionCurveSnapshotItem[]>(
            `/paper-trading/runs/${paperRunId}/position-curve`,
        );
        return data;
    },
    async replay(paperRunId: string): Promise<TradeReplayRecordItem[]> {
        const {data} = await apiClient.get<TradeReplayRecordItem[]>(
            `/paper-trading/runs/${paperRunId}/replay`,
        );
        return data;
    },
    async emergencyStop(
        paperRunId: string,
        request: EmergencyStopRequest,
    ): Promise<EmergencyStopEventItem> {
        // 原编号在发送前持久保留；任何不匹配或丢失的结果都交给只读恢复，不自动再停机。
        return submitResultAction('PAPER_EMERGENCY', paperRunId, async (operationId) => {
            const {data} = await apiClient.post<EmergencyStopEventItem>(
                `/paper-trading/runs/${paperRunId}/emergency-stop`,
                request,
                {headers: {'Idempotency-Key': operationId}},
            );
            return confirmEmergencyResult(data, paperRunId, operationId);
        });
    },
    async emergencyStopOriginalResult(paperRunId: string, operationId: string) {
        const {data} = await apiClient.get<EmergencyStopEventItem>(
            `/paper-trading/runs/${paperRunId}/emergency-stop-operations/${operationId}`,
        );
        return confirmEmergencyResult(data, paperRunId, operationId);
    },
    async emergencyStops(paperRunId: string): Promise<EmergencyStopEventItem[]> {
        const {data} = await apiClient.get<EmergencyStopEventItem[]>(
            `/paper-trading/runs/${paperRunId}/emergency-stops`,
        );
        return data;
    },
    async listSchedules(params: {
        paperRunId?: string;
        status?: string;
    }): Promise<PaperRunScheduleItem[]> {
        const {data} = await apiClient.get<PaperRunScheduleItem[]>('/paper-trading/schedules', {
            params,
        });
        return data;
    },
    async createSchedule(request: PaperRunScheduleCreateRequest): Promise<PaperRunScheduleItem> {
        const {data} = await apiClient.post<PaperRunScheduleItem>(
            '/paper-trading/schedules',
            request,
        );
        return data;
    },
    async scheduleDetail(scheduleId: string): Promise<PaperRunScheduleItem> {
        const {data} = await apiClient.get<PaperRunScheduleItem>(
            `/paper-trading/schedules/${scheduleId}`,
        );
        return data;
    },
    async updateScheduleStatus(
        scheduleId: string,
        request: PaperRunScheduleStatusUpdateRequest,
    ): Promise<PaperRunScheduleItem> {
        const {data} = await apiClient.patch<PaperRunScheduleItem>(
            `/paper-trading/schedules/${scheduleId}/status`,
            request,
        );
        return data;
    },
    async runScheduleOnce(scheduleId: string): Promise<PaperRunScheduleFireItem> {
        const {data} = await apiClient.post<PaperRunScheduleFireItem>(
            `/paper-trading/schedules/${scheduleId}/run-once`,
        );
        return data;
    },
    async listFires(scheduleId: string): Promise<PaperRunScheduleFireItem[]> {
        const {data} = await apiClient.get<PaperRunScheduleFireItem[]>(
            `/paper-trading/schedules/${scheduleId}/fires`,
        );
        return data;
    },
    async listHeartbeats(paperRunId: string): Promise<PaperRunHeartbeatItem[]> {
        const {data} = await apiClient.get<PaperRunHeartbeatItem[]>(
            `/paper-trading/runs/${paperRunId}/heartbeats`,
        );
        return data;
    },
    async runHeartbeatOnce(paperRunId: string): Promise<PaperRunHeartbeatItem> {
        const {data} = await apiClient.post<PaperRunHeartbeatItem>(
            `/paper-trading/runs/${paperRunId}/heartbeats/run-once`,
        );
        return data;
    },
    async listDailyReports(paperRunId: string): Promise<PaperRunDailyReportItem[]> {
        const {data} = await apiClient.get<PaperRunDailyReportItem[]>(
            `/paper-trading/runs/${paperRunId}/daily-reports`,
        );
        return data;
    },
    async generateDailyReport(
        paperRunId: string,
        request: PaperRunDailyReportGenerateRequest,
    ): Promise<PaperRunDailyReportItem> {
        const {data} = await apiClient.post<PaperRunDailyReportItem>(
            `/paper-trading/runs/${paperRunId}/daily-reports/generate`,
            request,
        );
        return data;
    },
    async listAlerts(
        paperRunId: string,
        params?: {status?: string; severity?: string},
    ): Promise<PaperRunAlertItem[]> {
        const {data} = await apiClient.get<unknown>(`/paper-trading/runs/${paperRunId}/alerts`, {
            params,
        });
        if (!Array.isArray(data))
            throw Object.assign(new Error('PAPER_ALERT_RESPONSE_INVALID'), {
                status: 0,
                code: 'PAPER_ALERT_RESPONSE_INVALID',
            });
        const ids = new Set<string>();
        return data.map((value) => {
            const row = parsePaperAlert(value, paperRunId);
            if (ids.has(row.alertId))
                throw Object.assign(new Error('PAPER_ALERT_RESPONSE_INVALID'), {
                    status: 0,
                    code: 'PAPER_ALERT_RESPONSE_INVALID',
                });
            ids.add(row.alertId);
            return row;
        });
    },
    async getAlert(
        paperRunId: string,
        alertId: string,
        signal?: AbortSignal,
    ): Promise<PaperRunAlertItem> {
        const {data} = await apiClient.get<unknown>(
            `/paper-trading/runs/${encodeURIComponent(paperRunId)}/alerts/${encodeURIComponent(alertId)}`,
            {signal, timeout: 15000},
        );
        return parsePaperAlert(data, paperRunId, alertId);
    },
    async createAlert(
        paperRunId: string,
        request: PaperRunAlertCreateRequest,
    ): Promise<PaperRunAlertItem> {
        return (await paperAlertOperations.submit(paperRunId, {type: 'CREATE', ...request})).result;
    },
    async ackAlert(
        paperRunId: string,
        alertId: string,
        _request?: PaperRunAlertAckRequest,
    ): Promise<PaperRunAlertItem> {
        // 确认人由认证主体决定；保留调用签名，旧客户端字段不再自报操作者。
        return (await paperAlertOperations.submit(paperRunId, {type: 'ACK', alertId})).result;
    },
    async resolveAlert(paperRunId: string, alertId: string): Promise<PaperRunAlertItem> {
        return (await paperAlertOperations.submit(paperRunId, {type: 'RESOLVE', alertId})).result;
    },
    async listRecoveryEvents(
        paperRunId: string,
        params?: {recoveryType?: string; status?: string},
    ): Promise<PaperRunRecoveryEventItem[]> {
        const {data} = await apiClient.get<PaperRunRecoveryEventItem[]>(
            `/paper-trading/runs/${paperRunId}/recovery-events`,
            {params},
        );
        return data;
    },
    async recover(
        paperRunId: string,
        request?: PaperRunRecoverRequest,
    ): Promise<PaperRunRecoveryEventItem> {
        const {data} = await apiClient.post<PaperRunRecoveryEventItem>(
            `/paper-trading/runs/${paperRunId}/recover`,
            request ?? {},
        );
        return data;
    },
    async retryFailedStep(
        paperRunId: string,
        request?: PaperRunRetryFailedStepRequest,
    ): Promise<PaperRunRecoveryEventItem> {
        const {data} = await apiClient.post<PaperRunRecoveryEventItem>(
            `/paper-trading/runs/${paperRunId}/retry-failed-step`,
            request ?? {},
        );
        return data;
    },
    async listStabilityChecks(
        paperRunId: string,
        params?: {status?: string},
    ): Promise<PaperRunStabilityCheckItem[]> {
        const {data} = await apiClient.get<PaperRunStabilityCheckItem[]>(
            `/paper-trading/runs/${paperRunId}/stability-checks`,
            {params},
        );
        return data;
    },
    async generateStabilityCheck(
        paperRunId: string,
        request: PaperRunStabilityCheckGenerateRequest,
    ): Promise<PaperRunStabilityCheckItem> {
        const {data} = await apiClient.post<PaperRunStabilityCheckItem>(
            `/paper-trading/runs/${paperRunId}/stability-checks/generate`,
            request,
        );
        return data;
    },
    async runMonitorOnce(paperRunId: string): Promise<PaperRunMonitorRunOnceResponse> {
        const {data} = await apiClient.post<PaperRunMonitorRunOnceResponse>(
            `/paper-trading/runs/${paperRunId}/monitor/run-once`,
            {},
        );
        return data;
    },
};

export interface CanonicalSimOrderDetails {
    paperRunId: string;
    order: Record<string, unknown>;
    trades: Record<string, unknown>[];
    ledgerEntries: Record<string, unknown>[];
    riskEvents: Record<string, unknown>[];
    relatedRecordsLimited: boolean;
}

/** 原事件身份与父运行必须同时匹配；APPLIED与FAILED都是已确认事实，UNKNOWN不是FAILED。 */
function confirmEmergencyResult(
    result: EmergencyStopEventItem,
    runId: string,
    operationId: string,
) {
    if (
        !result ||
        result.emergencyStopId !== 'es-' + operationId ||
        result.paperRunId !== runId ||
        !['APPLIED', 'FAILED'].includes(result.status)
    ) {
        throw Object.assign(new Error('PAPER_EMERGENCY_RESULT_UNCONFIRMED'), {
            status: 0,
            code: 'PAPER_EMERGENCY_RESULT_UNCONFIRMED',
            fieldErrors: [],
        });
    }
    return result;
}
