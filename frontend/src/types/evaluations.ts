export interface BacktestEvaluationListItem {
    researchValidity?: ResearchValidity | null;
    evalReportId: string;
    backtestRunId: string;
    evaluationStatus: string;
    evaluatedAt: string | null;
    initialCapital: number | null;
    finalEquity: number | null;
    netPnl: number | null;
    totalReturnRate: number | null;
    totalReturn: number | null;
    annualizedReturn: number | null;
    maxDrawdown: number | null;
    maxDrawdownRate: number | null;
    winRate: number | null;
    profitLossRatio: number | null;
    sharpeRatio: number | null;
    orderCount: number | null;
    tradeCount: number | null;
    metricsJson: string | null;
    failureCode: string | null;
    failureMessage: string | null;
}

export interface EvaluationsListFilters {
    researchConfigId: string;
    backtestConfigId: string;
    sourceStrategyId: string;
    evaluationStatus: string;
}

export const defaultEvaluationsListFilters: EvaluationsListFilters = {
    researchConfigId: '',
    backtestConfigId: '',
    sourceStrategyId: '',
    evaluationStatus: '',
};

export interface BacktestEvaluationDetailItem {
    researchValidity?: ResearchValidity | null;
    evalReportId: string;
    backtestRunId: string;
    evaluationStatus: string;
    evaluatedAt: string | null;
    initialCapital: number | null;
    finalCashBalance: number | null;
    finalPositionMarketValue: number | null;
    finalEquity: number | null;
    realizedPnl: number | null;
    unrealizedPnl: number | null;
    netPnl: number | null;
    totalReturnRate: number | null;
    totalReturn: number | null;
    annualizedReturn: number | null;
    totalFee: number | null;
    totalSlippage: number | null;
    orderCount: number | null;
    tradeCount: number | null;
    winningTradeCount: number | null;
    losingTradeCount: number | null;
    flatTradeCount: number | null;
    winRate: number | null;
    maxDrawdown: number | null;
    maxDrawdownRate: number | null;
    profitLossRatio: number | null;
    sharpeRatio: number | null;
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
    startingEquity: number | null;
    finalEquity: number | null;
    strategyReturn: number | null;
    netPnl: number | null;
    maxDrawdown: number | null;
    maxDrawdownRate: number | null;
    tradeCount: number | null;
    fee: number | null;
    slippage: number | null;
}

export interface ResearchValidity {
    validationStatus: string;
    reason: string | null;
    splitPolicy: string;
    segmentEquityPolicy: string;
    identity: {backtestRunId: string; datasetId: string; barContentSha256: string;
        strategyVersionId: string; strategyChecksum: string; symbol: string; interval: string;
        datasetSnapshotJson: string; evaluatedAt: string} | null;
    assumptions: {initialCapital: number; requestedInitialCapital: number; feeRate: number; slippageBps: number;
        executionTimingPolicy: string; finalEquityPolicy: string; executionSpecJson: string} | null;
    full: ResearchSegment | null;
    inSample: ResearchSegment | null;
    outOfSample: ResearchSegment | null;
    benchmark: {status: string; reason: string | null; benchmarkType: string; valuationPolicy: string;
        startTime: string | null; endTime: string | null; entryTime: string | null;
        entryPrice: number | null; endPrice: number | null; initialCapital: number | null;
        quantity: number | null; fee: number | null; slippage: number | null;
        finalEquity: number | null; benchmarkReturn: number | null} | null;
    strategyVsBenchmarkDifference: number | null;
}
