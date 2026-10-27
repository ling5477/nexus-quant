import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import {validateResultPage} from '@/api/result-page';

export interface ComparisonPerformance {
    phase: 'TRAINING' | 'WALK_FORWARD_OOS' | 'FINAL_HOLDOUT' | 'CONTINUOUS_SIM_FORWARD';
    fold: number | null;
    strategyVersionId: string | null;
    status: 'AVAILABLE' | 'NOT_COMPARABLE' | 'UNAVAILABLE';
    reason: string | null;
    from: string | null;
    to: string | null;
    netPnl: string | null;
    totalReturn: string | null;
    maxDrawdown: string | null;
    fee: string | null;
    feeCurrency: string | null;
    feeStatus: string;
    tradeCount: number | null;
    backtestConfigId: string | null;
    backtestRunId: string | null;
    evaluationId: string | null;
}
export interface ComparisonDecision {
    decisionId: string;
    strategyVersionId: string;
    signalOpenTime: string | null;
    signalAvailableAt: string | null;
    expectedAction: string | null;
    actualDecision: string | null;
    decisionStatus: string;
    executionOpenTime: string | null;
    orderStatus: string | null;
    fillStatus: string | null;
    fee: string | null;
    feeCurrency: string | null;
    slippage: string | null;
    status: 'MATCH' | 'DIFFERENT' | 'NOT_COMPARABLE' | 'UNAVAILABLE';
    attribution: string[];
    reason: string | null;
}
export interface ResearchSimComparison {
    validationId: string;
    selectedTrialId: string;
    strategyVersionId: string;
    strategyChecksum: string;
    publishId: string | null;
    paperRunId: string | null;
    backtestConfigId: string;
    backtestRunId: string;
    status: string;
    reason: string;
    performance: ComparisonPerformance[];
    observation: {
        state: 'SIM_NOT_STARTED' | 'SIM_RUNNING' | 'SIM_STOPPED';
        status: 'OBSERVATION_INCOMPLETE' | 'OBSERVATION_AVAILABLE';
        reason: string | null;
        from: string | null;
        to: string | null;
        cursor: Record<string, unknown> | null;
    };
    decisions: {
        status: string;
        reason: string | null;
        items: ComparisonDecision[];
        total: number;
        limit: number;
        windowLimited: boolean;
    };
}
export interface ComparisonSelection {
    selectedTrialId: string;
    strategyVersionId: string;
    publishId?: string;
    paperRunId?: string;
}
export interface PublishedSimIdentity {
    paperRunId: string;
    publishId: string;
    strategyVersionId: string | null;
    status: string;
}
const decimals = new Set(['netPnl', 'totalReturn', 'maxDrawdown', 'fee', 'slippage']);

/** 对照仅查询正式身份和原始事实；不会创建发布、运行或交易请求。 */
export const researchSimComparisonApi = {
    async get(validationId: string, selection: ComparisonSelection, signal?: AbortSignal) {
        const {data} = await apiClient.get<ResearchSimComparison>(
            `/strategy-experiments/validations/${encodeURIComponent(validationId)}/sim-comparison`,
            {params: selection, signal, transformResponse: [(raw: string) => parseExactJson(raw, decimals)]},
        );
        return data;
    },
    async sims(publishId: string, offset: number, signal?: AbortSignal) {
        const request = {limit: 10, offset};
        const {data} = await apiClient.get<unknown>(`/publishes/${encodeURIComponent(publishId)}/sim-runs/page`, {params: request, signal});
        return validateResultPage<PublishedSimIdentity>(data, request, value => {
            if (!value || typeof value !== 'object') return null;
            const row = value as Partial<PublishedSimIdentity>;
            return row.publishId === publishId && typeof row.paperRunId === 'string' && row.paperRunId
                && ['CREATED', 'RUNNING', 'STOPPED', 'FAILED'].includes(row.status ?? '') ? row.paperRunId : null;
        });
    },
};
