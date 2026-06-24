export interface PaperTradingRunItem {
    paperRunId: string;
    publishId: string;
    strategyVersionId: string | null;
    status: string;
    tradeEnv: string;
    exchangeCode: string;
    marketType: string;
    symbol: string;
    intervalCode: string;
    startedAt: string | null;
    stoppedAt: string | null;
    publishSnapshotJson: string | null;
    strategyVersionSnapshotJson: string | null;
    datasetSnapshotJson: string | null;
    paramSnapshotJson: string | null;
    configSnapshotJson: string | null;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

export interface PaperTradingOrderItem {
    paperOrderId: string;
    paperRunId: string;
    symbol: string;
    side: string;
    orderType: string;
    quantity: string | number | null;
    price: string | number | null;
    status: string;
    reason: string | null;
    rawSignalJson: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface PaperTradingTradeItem {
    paperTradeId: string;
    paperOrderId: string;
    paperRunId: string;
    symbol: string;
    side: string;
    quantity: string | number | null;
    price: string | number | null;
    fee: string | number | null;
    tradedAt: string;
    createdAt: string;
}

export interface PaperTradingPositionItem {
    paperPositionId: string;
    paperRunId: string;
    symbol: string;
    quantity: string | number | null;
    avgPrice: string | number | null;
    unrealizedPnl: string | number | null;
    realizedPnl: string | number | null;
    updatedAt: string;
    createdAt: string;
}

export interface PaperTradingRunCreateRequest {
    publishId: string;
    tradeEnv: string;
    exchangeCode: string;
    marketType: string;
    symbol: string;
    intervalCode: string;
    configSnapshotJson?: string;
}

export interface PaperTradingListFilters {
    publishId: string;
    status: string;
}

export const defaultPaperTradingListFilters: PaperTradingListFilters = {
    publishId: '',
    status: '',
};

export interface PaperRiskCheckResultItem {
    riskResultId: string;
    paperRunId: string;
    checkType: string;
    status: string;
    severity: string;
    message: string | null;
    inputSnapshotJson: string | null;
    resultSnapshotJson: string | null;
    createdAt: string;
}

export interface EquityCurveSnapshotItem {
    equitySnapshotId: string;
    paperRunId: string;
    snapshotTime: string;
    totalEquity: string | number;
    cashBalance: string | number;
    positionValue: string | number;
    unrealizedPnl: string | number;
    realizedPnl: string | number;
    drawdown: string | number;
    source: string;
    createdAt: string;
}

export interface PositionCurveSnapshotItem {
    positionSnapshotId: string;
    paperRunId: string;
    symbol: string;
    snapshotTime: string;
    quantity: string | number;
    avgPrice: string | number;
    markPrice: string | number;
    positionValue: string | number;
    unrealizedPnl: string | number;
    realizedPnl: string | number;
    source: string;
    createdAt: string;
}

export interface TradeReplayRecordItem {
    replayRecordId: string;
    paperRunId: string;
    paperOrderId: string | null;
    paperTradeId: string | null;
    replayTime: string;
    eventType: string;
    symbol: string;
    side: string | null;
    price: string | number | null;
    quantity: string | number | null;
    reason: string | null;
    decisionSnapshotJson: string | null;
    riskSnapshotJson: string | null;
    marketSnapshotJson: string | null;
    createdAt: string;
}

export interface EmergencyStopEventItem {
    emergencyStopId: string;
    paperRunId: string;
    triggerType: string;
    status: string;
    reason: string | null;
    triggeredBy: string | null;
    triggeredAt: string;
    resolvedAt: string | null;
    requestJson: string | null;
    resultJson: string | null;
    createdAt: string;
}

export interface EmergencyStopRequest {
    triggerType: string;
    reason: string;
    triggeredBy?: string;
}

export interface PaperRunScheduleItem {
    scheduleId: string;
    paperRunId: string;
    scheduleName: string;
    cronExpr: string;
    status: string;
    timezone: string;
    nextFireTime: string | null;
    lastFireTime: string | null;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
    requestJson: string | null;
}

export interface PaperRunScheduleFireItem {
    fireId: string;
    scheduleId: string;
    paperRunId: string;
    status: string;
    firedAt: string;
    finishedAt: string | null;
    durationMs: number | null;
    resultJson: string | null;
    errorMessage: string | null;
    createdAt: string;
}

export interface PaperRunHeartbeatItem {
    heartbeatId: string;
    paperRunId: string;
    heartbeatTime: string;
    status: string;
    lastEventTime: string | null;
    lastOrderTime: string | null;
    lastTradeTime: string | null;
    lagSeconds: number | null;
    summaryJson: string | null;
    createdAt: string;
}

export interface PaperRunScheduleCreateRequest {
    paperRunId: string;
    scheduleName: string;
    cronExpr: string;
    timezone?: string;
    requestJson?: string;
}

export interface PaperRunScheduleStatusUpdateRequest {
    status: string;
}

export interface PaperRunDailyReportItem {
    reportId: string;
    paperRunId: string;
    reportDate: string;
    status: string;
    totalEquity: string | number | null;
    dailyPnl: string | number | null;
    dailyReturn: string | number | null;
    maxDrawdown: string | number | null;
    orderCount: number;
    tradeCount: number;
    alertCount: number;
    riskRejectCount: number;
    reportJson: string | null;
    generatedAt: string;
    createdAt: string;
}

export interface PaperRunDailyReportGenerateRequest {
    reportDate?: string;
}

export interface PaperRunAlertItem {
    alertId: string;
    paperRunId: string;
    alertType: string;
    severity: string;
    status: string;
    title: string;
    message: string | null;
    source: string | null;
    eventSnapshotJson: string | null;
    acknowledgedBy: string | null;
    acknowledgedAt: string | null;
    resolvedAt: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface PaperRunAlertCreateRequest {
    alertType: string;
    severity: string;
    title: string;
    message?: string;
    source?: string;
    eventSnapshotJson?: string;
}

export interface PaperRunAlertAckRequest {
    acknowledgedBy?: string;
}

export interface PaperRunRecoveryEventItem {
    recoveryEventId: string;
    paperRunId: string;
    recoveryType: string;
    status: string;
    reason: string | null;
    requestJson: string | null;
    resultJson: string | null;
    startedAt: string;
    finishedAt: string | null;
    createdAt: string;
}

export interface PaperRunRecoverRequest {
    reason?: string;
    requestJson?: string;
}

export interface PaperRunRetryFailedStepRequest {
    failedStep?: string;
    reason?: string;
    requestJson?: string;
}

export interface PaperRunStabilityCheckItem {
    stabilityCheckId: string;
    paperRunId: string;
    checkWindowStart: string;
    checkWindowEnd: string;
    status: string;
    uptimeRatio: string | number;
    heartbeatCount: number;
    alertCount: number;
    failedFireCount: number;
    recoveryCount: number;
    reportCount: number;
    summaryJson: string | null;
    createdAt: string;
}

export interface PaperRunStabilityCheckGenerateRequest {
    checkWindowStart: string;
    checkWindowEnd: string;
}

export interface PaperRunMonitorRunOnceResponse {
    paperRunId: string;
    checkedAt: string;
    createdAlertCount: number;
    createdAlerts: PaperRunAlertItem[];
}

/**
 * Paper run 只读聚合响应（GateJ 后产品化 Loop-8）。
 * 详情区优先消费 summary 渲染复盘 / 诊断 / 时间线 / 关键指标；明细查询保留为表格数据源与 fallback。
 */
export interface PaperRunSummaryCounts {
    orderCount: number;
    tradeCount: number;
    fillCount: number;
    positionCount: number;
    openAlertCount: number;
    recoveryEventCount: number;
}

export interface PaperRunSummaryLatest {
    order: PaperTradingOrderItem | null;
    trade: PaperTradingTradeItem | null;
    position: PaperTradingPositionItem | null;
    equitySnapshot: EquityCurveSnapshotItem | null;
    dailyReport: PaperRunDailyReportItem | null;
    riskResult: PaperRiskCheckResultItem | null;
    alert: PaperRunAlertItem | null;
    recoveryEvent: PaperRunRecoveryEventItem | null;
}

export interface PaperRunSummaryResultReview {
    finalStatus: string;
    runtimeDurationText: string;
    netPnl: string | number | null;
    riskResult: string;
    conclusion: string;
    conclusionLevel: 'info' | 'warning' | 'danger';
}

export interface PaperRunSummaryDiagnosis {
    type: string;
    severity: 'INFO' | 'WARNING' | 'BLOCKING';
    title: string;
    description: string;
    checkTarget: string;
}

export interface PaperRunSummaryTimelineEntry {
    type: string;
    status: string;
    occurredAt: string;
    title: string;
    description: string;
}

export interface PaperRunSummarySafety {
    environment: string;
    liveEnabled: boolean;
    realExchangeTouched: boolean;
    message: string;
}

export interface PaperRunSummaryResponse {
    run: PaperTradingRunItem;
    counts: PaperRunSummaryCounts;
    latest: PaperRunSummaryLatest;
    resultReview: PaperRunSummaryResultReview;
    diagnoses: PaperRunSummaryDiagnosis[];
    timeline: PaperRunSummaryTimelineEntry[];
    safety: PaperRunSummarySafety;
}
