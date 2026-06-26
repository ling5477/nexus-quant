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

/**
 * Paper 组合看板只读聚合响应（GateJ 后产品化 Loop-13）。
 * 跨多个 Paper run 聚合组合总览、策略/发布排行、Run 排行与数据质量；
 * 收益率 / 回撤为比例值，数据不足时为 null（不外推、不伪造收益率），仅代表 SIM/Paper。
 */
export interface PaperPortfolioOverview {
    totalRuns: number;
    runningCount: number;
    stoppedCount: number;
    failedCount: number;
    cancelledCount: number;
    createdCount: number;
    totalInitialEquity: string | number | null;
    totalCurrentEquity: string | number | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    returnEligibleRunCount: number;
    worstRunDrawdown: string | number | null;
    openAlertCount: number;
    riskBlockedRunCount: number;
    noTradeRunCount: number;
    dataInsufficientRunCount: number;
    // Loop-18 向后兼容新增：把「无交易」拆为「无订单」「有订单无成交」并显式给出「有成交」run 数；
    // 旧后端响应可能缺失，消费方需对 undefined 做 fallback（noOrderRunCount + orderNoFillRunCount == noTradeRunCount）。
    noOrderRunCount?: number;
    orderNoFillRunCount?: number;
    filledRunCount?: number;
}

export interface PaperPortfolioGroup {
    key: string;
    runCount: number;
    currentEquity: string | number | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    worstDrawdown: string | number | null;
    riskBlockedCount: number;
    openAlertCount: number;
    lastRunTime: string | null;
    // Loop-17 组内精确计数（向后兼容新增，基于完整 bounded runs）；旧后端响应可能缺失，消费方需对 undefined 做 fallback。
    noTradeCount?: number;
    dataInsufficientCount?: number;
    comparableRunCount?: number;
    runningCount?: number;
    stoppedCount?: number;
    failedCount?: number;
    cancelledCount?: number;
    createdCount?: number;
    // Loop-18 组内订单/成交总数与执行进度三态 run 计数（向后兼容新增）；
    // noOrderCount + orderNoFillCount == noTradeCount（旧字段语义不变）。
    orderCount?: number;
    tradeCount?: number;
    noOrderCount?: number;
    orderNoFillCount?: number;
    filledRunCount?: number;
}

export interface PaperPortfolioRunRef {
    paperRunId: string;
    status: string;
    symbol: string;
    strategyVersionId: string | null;
    publishId: string;
    currentEquity: string | number | null;
    initialEquity: string | number | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    maxDrawdown: string | number | null;
    riskBlocked: boolean;
    openAlertCount: number;
    tradeCount: number;
    lastActivityAt: string | null;
    // Loop-18 向后兼容新增：orderCount 与执行进度三态标记；旧后端响应可能缺失，消费方需对 undefined 做 fallback。
    orderCount?: number;
    noOrder?: boolean;
    orderNoFill?: boolean;
    hasFill?: boolean;
}

export interface PaperPortfolioHighlights {
    topWinner: PaperPortfolioRunRef | null;
    worstDrawdown: PaperPortfolioRunRef | null;
    highestRisk: PaperPortfolioRunRef | null;
    mostRecent: PaperPortfolioRunRef | null;
    noTradeRuns: PaperPortfolioRunRef[];
    riskBlockedRuns: PaperPortfolioRunRef[];
}

export interface PaperPortfolioDataQuality {
    missingEquityRuns: PaperPortfolioRunRef[];
    dataInsufficientRuns: PaperPortfolioRunRef[];
    missingBacktestSourceRuns: PaperPortfolioRunRef[];
    missingPublishSourceRuns: PaperPortfolioRunRef[];
}

export interface PaperPortfolioSafety {
    environment: string;
    liveEnabled: boolean;
    realExchangeTouched: boolean;
    message: string;
}

/**
 * 组合级 equity / drawdown 时间序列（Loop-15 向后兼容新增）。
 * 仅代表 SIM/Paper 模拟、简化组合资金合计曲线；数据不足时 points 为空、指标为 null（不伪造回撤）。
 */
export interface PaperPortfolioCurvePoint {
    timestamp: string;
    totalEquity: string | number | null;
    totalInitialEquity: string | number | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    peakEquity: string | number | null;
    drawdown: string | number | null;
    sourceRunCount: number;
    missingRunCount: number;
}

export interface PaperPortfolioCurveCoverage {
    comparableRunCount: number;
    missingEquityRunCount: number;
    incompletePointCount: number;
}

export interface PaperPortfolioCurve {
    points: PaperPortfolioCurvePoint[];
    latestEquity: string | number | null;
    peakEquity: string | number | null;
    currentDrawdown: string | number | null;
    maxDrawdown: string | number | null;
    pointCount: number;
    coverage: PaperPortfolioCurveCoverage;
}

export interface PaperPortfolioSummaryResponse {
    overview: PaperPortfolioOverview;
    strategyGroups: PaperPortfolioGroup[];
    publishGroups: PaperPortfolioGroup[];
    highlights: PaperPortfolioHighlights;
    dataQuality: PaperPortfolioDataQuality;
    safety: PaperPortfolioSafety;
    // 向后兼容：旧后端响应可能不含该字段；消费方需对 null/undefined/空 points 做 fallback。
    portfolioCurve?: PaperPortfolioCurve | null;
}

/**
 * Paper 执行诊断只读聚合响应（GateK Batch K1/K2）。
 * 对 bounded Paper run 做规则化归因（无订单/有订单无成交/成交亏损/风控拦截/数据不足/高回撤/异常终态），
 * 并按 strategyVersionId / publishId 聚合定位问题集中点。仅 Paper-only 规则化归因，不构成真实投资建议。
 */
export type PaperExecutionCause =
    | 'NO_ORDER'
    | 'ORDER_NO_FILL'
    | 'FILLED_LOSS'
    | 'RISK_BLOCKED'
    | 'DATA_INSUFFICIENT'
    | 'HIGH_DRAWDOWN'
    | 'FAILED_RUN'
    | 'RUNNING_NO_RESULT'
    | 'HEALTHY'
    | 'UNKNOWN';

export type PaperExecutionSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export type PaperExecutionCauseConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

export interface PaperExecutionDiagnosticsOverview {
    totalRuns: number;
    noOrderRunCount: number;
    orderNoFillRunCount: number;
    filledRunCount: number;
    filledLossRunCount: number;
    riskBlockedRunCount: number;
    dataInsufficientRunCount: number;
    highDrawdownRunCount: number;
    failedRunCount: number;
    runningRunCount: number;
}

export interface PaperExecutionDiagnosticCauseDistribution {
    cause: PaperExecutionCause;
    count: number;
    severity: PaperExecutionSeverity;
    confidence: PaperExecutionCauseConfidence;
    description: string;
}

export interface PaperExecutionRunDiagnostic {
    paperRunId: string;
    strategyVersionId: string | null;
    publishId: string | null;
    status: string;
    orderCount: number;
    tradeCount: number;
    currentEquity: string | number | null;
    initialEquity: string | number | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    maxDrawdown: string | number | null;
    riskBlocked: boolean;
    openAlertCount: number;
    primaryCause: PaperExecutionCause;
    secondaryCauses: PaperExecutionCause[];
    severity: PaperExecutionSeverity;
    causeConfidence: PaperExecutionCauseConfidence;
    explanation: string;
    suggestedAction: string;
    lastRunTime: string | null;
}

/** strategyVersionId / publishId 维度聚合诊断；key 为对应维度标识（后端口径）。 */
export interface PaperExecutionGroupDiagnostic {
    key: string;
    runCount: number;
    primaryCause: PaperExecutionCause;
    topCauses: PaperExecutionCause[];
    noOrderCount: number;
    orderNoFillCount: number;
    filledLossCount: number;
    riskBlockedCount: number;
    dataInsufficientCount: number;
    highDrawdownCount: number;
    severity: PaperExecutionSeverity;
    causeConfidence: PaperExecutionCauseConfidence;
}

export interface PaperExecutionDiagnosticsSafety {
    environment: string;
    liveEnabled: boolean;
    realExchangeTouched: boolean;
    message: string;
}

export interface PaperExecutionDiagnosticsResponse {
    overview: PaperExecutionDiagnosticsOverview;
    causeDistribution: PaperExecutionDiagnosticCauseDistribution[];
    runDiagnostics: PaperExecutionRunDiagnostic[];
    strategyDiagnostics: PaperExecutionGroupDiagnostic[];
    publishDiagnostics: PaperExecutionGroupDiagnostic[];
    safety: PaperExecutionDiagnosticsSafety;
}

/**
 * Paper 策略评估只读聚合响应（GateK Batch K3/K3B）。
 * 从 strategyVersionId / publishId 维度评估 Paper 模拟表现、Paper vs Backtest 偏差、样本充足性与风险调整评分。
 * 评分为 0~100 Paper 内部启发式分，不是真实投资评级、不代表 LIVE 或真实交易表现、不构成投资建议。
 */
export type PaperStrategyRatingLabel =
    | 'STRONG_PAPER_PERFORMER'
    | 'WATCHLIST'
    | 'HIGH_RISK'
    | 'SAMPLE_INSUFFICIENT'
    | 'DATA_INSUFFICIENT'
    | 'EXECUTION_PROBLEM'
    | 'UNKNOWN';

export type PaperStrategyEvaluationConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

export type PaperBacktestDeviationLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNAVAILABLE';

export interface PaperStrategyEvaluationOverview {
    strategyCount: number;
    publishCount: number;
    evaluatedRunCount: number;
    comparableRunCount: number;
    sampleInsufficientStrategyCount: number;
    profitableStrategyCount: number;
    lossStrategyCount: number;
    highRiskStrategyCount: number;
    backtestDeviationStrategyCount: number;
    topCompositeScore: number | null;
    worstCompositeScore: number | null;
}

export interface PaperBacktestDeviation {
    backtestReturn: string | number | null;
    paperReturn: string | number | null;
    returnDeviation: string | number | null;
    backtestMaxDrawdown: string | number | null;
    paperMaxDrawdown: string | number | null;
    drawdownDeviation: string | number | null;
    deviationLevel: PaperBacktestDeviationLevel;
    deviationExplanation: string;
}

export interface PaperStrategyEvaluationItem {
    strategyVersionId: string;
    runCount: number;
    comparableRunCount: number;
    publishCount: number;
    latestRunTime: string | null;
    currentEquity: string | number | null;
    initialEquity: string | number | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    maxDrawdown: string | number | null;
    winRunCount: number;
    lossRunCount: number;
    winRate: string | number | null;
    averageReturn: string | number | null;
    averageDrawdown: string | number | null;
    riskBlockedCount: number;
    dataInsufficientCount: number;
    noOrderCount: number;
    orderNoFillCount: number;
    filledRunCount: number;
    filledLossCount: number;
    failedRunCount: number;
    sampleScore: number;
    riskScore: number;
    returnScore: number;
    executionScore: number;
    backtestDeviationScore: number | null;
    compositeScore: number;
    ratingLabel: PaperStrategyRatingLabel;
    evaluationConfidence: PaperStrategyEvaluationConfidence;
    primaryWeakness: string;
    warnings: string[];
    backtestDeviation: PaperBacktestDeviation | null;
}

export interface PaperPublishEvaluationItem {
    publishId: string;
    strategyVersionId: string | null;
    runCount: number;
    comparableRunCount: number;
    latestRunTime: string | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    maxDrawdown: string | number | null;
    winRate: string | number | null;
    sampleScore: number;
    riskScore: number;
    returnScore: number;
    executionScore: number;
    backtestDeviationScore: number | null;
    compositeScore: number;
    ratingLabel: PaperStrategyRatingLabel;
    evaluationConfidence: PaperStrategyEvaluationConfidence;
    warnings: string[];
    backtestDeviation: PaperBacktestDeviation | null;
}

export interface PaperStrategyEvaluationRankings {
    topCompositeStrategies: string[];
    worstCompositeStrategies: string[];
    topReturnStrategies: string[];
    worstDrawdownStrategies: string[];
    sampleInsufficientStrategies: string[];
    highDeviationStrategies: string[];
    highRiskStrategies: string[];
}

export interface PaperStrategyEvaluationSafety {
    environment: string;
    liveEnabled: boolean;
    realExchangeTouched: boolean;
    message: string;
}

export interface PaperStrategyEvaluationsResponse {
    overview: PaperStrategyEvaluationOverview;
    strategyEvaluations: PaperStrategyEvaluationItem[];
    publishEvaluations: PaperPublishEvaluationItem[];
    rankings: PaperStrategyEvaluationRankings;
    safety: PaperStrategyEvaluationSafety;
}
