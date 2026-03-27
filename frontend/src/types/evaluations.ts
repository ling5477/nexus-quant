export interface BacktestEvaluationListItem {
    backtestRunId: string;
    backtestConfigId: string;
    researchConfigId: string;
    sourceStrategyId: string;
    status: string;
    requestedAt: string;
    startedAt: string | null;
    finishedAt: string | null;
    evaluationStatus: string | null;
    evaluatedAt: string | null;
    totalReturnRate: number | null;
    maxDrawdownRate: number | null;
    winRate: number | null;
    sharpeRatio: number | null;
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
    sharpeRatio: number | null;
    reportJson: string | null;
    failureCode: string | null;
    failureMessage: string | null;
}
