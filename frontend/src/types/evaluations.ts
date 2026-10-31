export interface BacktestEvaluationListItem {
    researchValidity?: ResearchValidity | null;
    evalReportId: string;
    backtestRunId: string;
    evaluationStatus: string;
    evaluatedAt: string | null;
    initialCapital: number | string | null;
    finalEquity: number | string | null;
    netPnl: number | string | null;
    totalReturnRate: number | string | null;
    totalReturn: number | string | null;
    annualizedReturn: number | string | null;
    maxDrawdown: number | string | null;
    maxDrawdownRate: number | string | null;
    winRate: number | string | null;
    profitLossRatio: number | string | null;
    sharpeRatio: number | string | null;
    orderCount: number | null;
    tradeCount: number | null;
    metricsJson: string | null;
    failureCode: string | null;
    failureMessage: string | null;
}

export interface EvaluationsListFilters {
    researchConfigId: string;
    backtestConfigId: string;
    backtestRunId: string;
    evaluationStatus: string;
}

export interface EvaluationPageRequest extends EvaluationsListFilters {
    limit: number;
    offset: number;
}

/** 正式评估结果仍保留缺失状态与精确十进制，总数独立于当前页条数。 */
export interface EvaluationPage {
    items: BacktestEvaluationListItem[];
    total: number;
    limit: number;
    offset: number;
}

export const defaultEvaluationsListFilters: EvaluationsListFilters = {
    researchConfigId: '',
    backtestConfigId: '',
    backtestRunId: '',
    evaluationStatus: '',
};

export interface BacktestEvaluationDetailItem {
    /** 原次评估回执的正式UUID；详情应按此恢复，不能替换为最新报告。 */
    evaluationRequestId?: string;
    researchValidity?: ResearchValidity | null;
    evalReportId: string;
    backtestRunId: string;
    evaluationStatus: string;
    evaluatedAt: string | null;
    initialCapital: number | string | null;
    finalCashBalance: number | string | null;
    finalPositionMarketValue: number | string | null;
    finalEquity: number | string | null;
    realizedPnl: number | string | null;
    unrealizedPnl: number | string | null;
    netPnl: number | string | null;
    totalReturnRate: number | string | null;
    totalReturn: number | string | null;
    annualizedReturn: number | string | null;
    totalFee: number | string | null;
    totalSlippage: number | string | null;
    orderCount: number | null;
    tradeCount: number | null;
    winningTradeCount: number | null;
    losingTradeCount: number | null;
    flatTradeCount: number | null;
    winRate: number | string | null;
    maxDrawdown: number | string | null;
    maxDrawdownRate: number | string | null;
    profitLossRatio: number | string | null;
    sharpeRatio: number | string | null;
    reportJson: string | null;
    metricsJson: string | null;
    failureCode: string | null;
    failureMessage: string | null;
}

export interface ResearchSegment {
    status: string;
    startTime: string;
    endTime: string;
    barCount: number;
    startingEquity: number | string | null;
    finalEquity: number | string | null;
    strategyReturn: number | string | null;
    netPnl: number | string | null;
    maxDrawdown: number | string | null;
    maxDrawdownRate: number | string | null;
    tradeCount: number | null;
    fee: number | string | null;
    slippage: number | string | null;
}

export interface ResearchValidity {
    validationStatus: string;
    reason: string | null;
    splitPolicy: string;
    segmentEquityPolicy: string;
    identity: {
        backtestRunId: string;
        datasetId: string;
        barContentSha256: string;
        strategyVersionId: string;
        strategyChecksum: string;
        symbol: string;
        interval: string;
        datasetSnapshotJson: string;
        evaluatedAt: string;
    } | null;
    assumptions: {
        initialCapital: number | string;
        requestedInitialCapital: number | string;
        feeRate: number | string | null;
        slippageBps: number | string;
        executionTimingPolicy: string;
        finalEquityPolicy: string;
        executionSpecJson: string;
    } | null;
    full: ResearchSegment | null;
    inSample: ResearchSegment | null;
    outOfSample: ResearchSegment | null;
    benchmark: {
        status: string;
        reason: string | null;
        benchmarkType: string;
        valuationPolicy: string;
        startTime: string | null;
        endTime: string | null;
        entryTime: string | null;
        entryPrice: number | string | null;
        endPrice: number | string | null;
        initialCapital: number | string | null;
        quantity: number | string | null;
        fee: number | string | null;
        slippage: number | string | null;
        finalEquity: number | string | null;
        benchmarkReturn: number | string | null;
        feeCurrency?: string | null;
        feeNativeAmount?: number | string | null;
        feePaymentStatus?: string | null;
    } | null;
    strategyVsBenchmarkDifference: number | string | null;
}
