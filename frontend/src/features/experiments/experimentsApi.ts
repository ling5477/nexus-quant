import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import type {ResearchValidity} from '@/types/evaluations';

export interface ExperimentRequest {
    templateConfigId: string;
    candidates?: Record<string, number | string>[];
    grid?: Record<string, (number | string)[]>;
}
export interface ExperimentSummary {
    experimentId: string;
    strategyType: string;
    trialCount: number;
    cancelRequested: boolean;
    active: boolean;
    createdAt: string;
}
export interface ExperimentTrial {
    trialId: string;
    ordinal: number;
    parameterIdentity: string;
    parameters: Record<string, number | string>;
    strategyVersionId: string;
    attemptId: string;
    attempt: number;
    backtestConfigId: string;
    backtestRunId: string;
    status: string;
    failureReason: string | null;
    runStatus: string;
    evaluationId: string | null;
    evaluationStatus: string | null;
    comparable: boolean;
    metrics: {
        netPnl: string | null; totalReturn: string | null; maxDrawdown: string | null;
        sharpe: string | null; tradeCount: number | null; fee: string | null; slippage: string | null;
        metrics: {researchValidity?: ResearchValidity; sharpeUnavailableReason?: string} | null;
    };
}
export interface ExperimentView {
    experimentId: string;
    strategyType: string;
    status: string;
    cancelRequested: boolean;
    createdAt: string;
    frozenInput: {
        initialCapital: string; startTime: string; endTime: string;
        datasetSnapshot: {symbol: string; interval: string; barCount?: number; frozenInput: {sha256: string; version?: string; datasetVersionId?: string; barCount?: number; manifestSha256?: string}};
        executionSpec: Record<string, unknown>; maxConcurrentRuns: number; maxTrials: number;
    };
    trials: ExperimentTrial[];
}
const base = '/strategy-experiments';
const exact = {transformResponse: [(raw: string) => parseExactJson(raw, new Set([
    'initialCapital', 'finalEquity', 'startingEquity', 'netPnl', 'maxDrawdown', 'maxDrawdownRate',
    'benchmarkReturn', 'strategyReturn', 'strategyVsBenchmarkDifference', 'quantity', 'fee', 'slippage',
]))]};
export const experimentsApi = {
    async list(offset: number) {return (await apiClient.get<ExperimentSummary[]>(base, {params: {offset, limit: 20}})).data;},
    async get(id: string) {return (await apiClient.get<ExperimentView>(`${base}/${id}`, exact)).data;},
    async preview(input: ExperimentRequest) {return (await apiClient.post<Record<string, number | string>[]>(`${base}/preview`, input)).data;},
    async create(id: string, input: ExperimentRequest) {return (await apiClient.post<ExperimentView>(`${base}/operations/${id}`, input)).data;},
    async cancel(id: string) {return (await apiClient.post<ExperimentView>(`${base}/${id}/cancel`)).data;},
    async retry(id: string, trial: string, attempt: string) {return (await apiClient.post<ExperimentView>(`${base}/${id}/trials/${trial}/attempts/${attempt}`)).data;},
};
