import {apiClient} from '@/api/client';
import {parseSimAnalysisResponse, parseSimPerformanceResponse} from '../analysis-response';
import type {ClosedTradeOutcomes, PerformanceSharpe} from '@/features/accounts/types/performance';
import type {StrategySimDecision, StrategySimFacts} from '../types/paper-trading';
import {accountValuationCurrency} from '@/features/preferences/currencies';

export interface SimAnalysisRun {
    paperRunId: string;
    canonicalAccountId: number;
    status: string;
    symbol: string;
    intervalCode: string;
    publishId: string;
    strategyVersionId: string;
    strategyCode: string | null;
    versionName: string | null;
    version: number | null;
    backtestConfigId: string | null;
    backtestRunId: string | null;
    configSnapshotJson: string | null;
    createdAt: string;
    updatedAt: string;
}
export interface SimAnalysisCatalog {
    items: SimAnalysisRun[];
    offset: number;
    hasMore: boolean;
    windowLimited: boolean;
}

/** 重建序列沿用canonical经济事实；timeBasis区分回放模型时间与持续SIM真实时间，账务更新时间另列。 */
export interface SimPerformance {
    paperRunId: string;
    status: string;
    timeBasis: 'REPLAY_EVENT_TIME' | 'ACTUAL_EVENT_TIME';
    quoteCurrency: string;
    valuationCurrency?: string;
    coverageStart: string | null;
    coverageEnd: string | null;
    ledgerAsOf: string | null;
    initialEquity: string | null;
    finalEquity: string | null;
    pnl: string | null;
    returnRatio: string | null;
    maxDrawdown: string | null;
    maxDrawdownRatio: string | null;
    sharpe: PerformanceSharpe;
    outcomes: ClosedTradeOutcomes;
    tradesAfterCoverage: number;
    samples: Array<{
        decisionId: string;
        eventAt: string;
        markPrice: string;
        cash: string;
        positionQuantity: string;
        equity: string;
        markType: 'SIGNAL_CLOSE' | 'EXECUTION_QUOTE';
        valuationCurrency?: string;
        valuationTime?: string;
        valuationMethod?: string;
        valuationSource?: string;
        valuationStatus?: 'VERIFIED' | 'PARTIAL';
        markObservedAt?: string | null;
        markVisibleAt?: string | null;
    }>;
}
/** 单个canonical SIM的全量统计与有界决策明细；费用保留原币种和覆盖，不从明细窗口推算总数。 */
export interface SimAnalysis {
    run: SimAnalysisRun;
    facts: StrategySimFacts;
    metrics: {
        orderStatusCounts: Array<{status: string; count: number}>;
        tradeCount: number;
        fees: Array<{
            currency: string | null;
            totalFee: string | null;
            tradeCount: number;
            measuredFeeCount: number;
        }>;
        riskEventCount: number;
        decisionCount: number;
        decisionWindowLimited: boolean;
    };
    decisions: StrategySimDecision[];
}
/** 按精确运行身份读取授权数据；取消读取不停止运行，金额词法值由专用解析器保留。 */
export const simAnalysisApi = {
    async list(offset: number, signal?: AbortSignal): Promise<SimAnalysisCatalog> {
        return (
            await apiClient.get<SimAnalysisCatalog>('/paper-trading/strategy-sim/analysis/runs', {
                params: {offset},
                signal,
            })
        ).data;
    },
    async read(paperRunId: string, signal?: AbortSignal): Promise<SimAnalysis> {
        return (
            await apiClient.get<SimAnalysis>(
                `/paper-trading/strategy-sim/analysis/runs/${encodeURIComponent(paperRunId)}`,
                {signal, transformResponse: [parseSimAnalysisResponse]},
            )
        ).data;
    },
    async performance(
        paperRunId: string,
        from: string | null,
        to: string | null,
        signal?: AbortSignal,
    ): Promise<SimPerformance> {
        const data = (
            await apiClient.get<SimPerformance>(
                `/paper-trading/strategy-sim/analysis/runs/${encodeURIComponent(paperRunId)}/performance`,
                {
                    params: from && to ? {from, to} : {},
                    signal,
                    transformResponse: [parseSimPerformanceResponse],
                },
            )
        ).data;
        accountValuationCurrency(data);
        if (
            data.paperRunId !== paperRunId ||
            data.samples.some(
                (sample) =>
                    sample.valuationCurrency !== undefined &&
                    sample.valuationCurrency !== data.valuationCurrency,
            )
        )
            throw Error('SIM_PERFORMANCE_RESPONSE_SCOPE_MISMATCH');
        return data;
    },
};
