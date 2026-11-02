/** 派生权益采样；缺失估值及资金流用null表达，不能按零资产或零收益展示。 */
export interface AccountEquitySample {
    sampleId: string;
    exchangeAccountId: number;
    quoteCurrency: string;
    valuationCurrency?: string;
    sampledAt: string;
    equity: string | null;
    cumulativeNetFlow: string | null;
    status: 'AVAILABLE' | 'NO_BALANCE' | 'VALUATION_INCOMPLETE' | 'FLOW_INCOMPLETE';
    sourceJson: string;
}
/** 收益口径及实际覆盖范围由正式接口返回；历史截断不等于完整区间统计。 */
export interface AccountPerformance {
    summary: {
        exchangeAccountId: number;
        quoteCurrency: string;
        valuationCurrency?: string;
        scope: string;
        calculation: string;
        requestedStart: string;
        requestedEnd: string;
        coverageStart: string | null;
        coverageEnd: string | null;
        status: string;
        initialEquity: string | null;
        finalEquity: string | null;
        netFlow: string | null;
        pnl: string | null;
        returnRatio: string | null;
        maxDrawdown: string | null;
        maxDrawdownRatio: string | null;
        samples: AccountEquitySample[];
    };
    historyLimited: boolean;
    samplingIntervalSeconds: number;
    sharpe: PerformanceSharpe;
    outcomes: ClosedTradeOutcomes;
}

/** 两个统计共享明确的方法与缺失原因；缺失结果不能转换为0或100%。 */
export interface PerformanceSharpe {
    value: string | null;
    status: string;
    returnCount: number;
    samplingSeconds: number;
    method: string;
    riskFreeRate: string;
}
export interface ClosedTradeOutcomes {
    status: string;
    winRate: string | null;
    winningCount: number;
    losingCount: number;
    flatCount: number;
    netProfit: string | null;
    netLoss: string | null;
    profitLossRatio: string | null;
    method: string;
    coverageStart: string | null;
    coverageEnd: string | null;
}
