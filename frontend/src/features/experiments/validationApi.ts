import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import type {ResearchValidity} from '@/types/evaluations';

export interface ValidationScore {ordinal: number; trialId: string; versionId: string; runId: string; evaluationId: string; totalReturn: string; maxDrawdown: string}
export interface ValidationWindow {fold: number; role: string; startBar: number; endBar: number; startTime?: string; endTimeExclusive?: string; barCount?: number}
export interface ValidationPolicy {
    version: string; selectionPolicy: string; finalSelectionPolicy: string; barCount: number;
    supported?: boolean; maxTrials?: number; estimatedValidationRuns?: number; estimatedTotalRuns?: number;
    estimatedWindowBars?: number; maxAttemptRuns?: number;
    initialCapital: string; datasetSha256: string; steps: ValidationWindow[];
    candidates: {ordinal: number; sourceTrialId?: string; strategyVersionId: string; parameters: Record<string, string | number>}[];
    costHypotheses: Record<string, {source: string; field: string; unit: string; baseline: string; value: string} | 'UNAVAILABLE'>;
}

/** 服务端明确不支持时保持禁用；兼容旧720政策响应，不由前端生成另一套窗口。 */
export function supportsValidationPolicy(policy: ValidationPolicy) {
    return policy.supported !== false && policy.steps.length === 16
        && ((policy.version === 'EXPANDING_WALK_FORWARD_V1' && policy.barCount === 720)
            || (policy.version === 'EXPANDING_WALK_FORWARD_V2' && [2160, 4320].includes(policy.barCount)));
}
export interface ValidationStep extends ValidationWindow {
    number: number; experimentId: string | null; unavailableReason: string | null; selectedAt: string | null;
    selection: {policy: string; selected?: ValidationScore; rankings: ValidationScore[]} | null;
}
export interface ValidationResult {
    step: number; ordinal: number; strategyVersionId: string; backtestConfigId: string; backtestRunId: string;
    status: string; failureReason: string | null; evaluationId: string | null; evaluatedAt: string | null;
    totalReturn: string | null; netPnl: string | null; maxDrawdown: string | null; tradeCount: number | null;
    researchValidity: ResearchValidity | null; summary: Record<string, unknown>; executionSpec: Record<string, unknown>;
}
export interface ValidationSummary {validationId: string; status: string; failureReason: string | null; createdAt: string}
export interface SelectedStrategyVersion {
    validationId: string; selectedTrialId: string; strategyVersionId: string; strategyCode: string;
    version: number; status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'; checksum: string;
    parameters: Record<string, string | number>; backtestConfigId: string; backtestRunId: string;
    eligible: boolean; updatedAt: string;
}
export interface ValidationView extends ValidationSummary {
    sourceExperimentId: string; frozenPolicy: ValidationPolicy; steps: ValidationStep[]; results: ValidationResult[];
    parameterStability: {
        completedOosFolds: number; meanIndependentOosReturn?: string; largestWindowAbsoluteReturnShare?: string; windowDependence?: string;
        rankChanges: {candidate: {ordinal: number; parameters: Record<string, string | number>}; foldRanks: {fold: number; rank?: number; status?: string}[]}[];
        adjacentCandidateDifferences: {fold: number; a: number; b: number; trainingReturnDifferenceBMinusA?: string; status?: string}[];
    };
    holdoutViews: {viewId: string; viewedAt: string}[]; datasetValidationUses: number;
    costSensitivity: {fold: number; scenario: string; status: string; reason?: string; baselineNetPnl?: string; stressNetPnl?: string; returnChange?: string; maxDrawdownChange?: string; tradeCountChange?: number; benchmarkRelative?: string}[];
}
const base = '/strategy-experiments';
const exact = {transformResponse: [(raw: string) => parseExactJson(raw, new Set([
    'totalReturn', 'maxDrawdown', 'initialCapital', 'benchmarkReturn', 'strategyVsBenchmarkDifference',
]))]};
export const validationApi = {
    async selectedVersion(id: string) {return (await apiClient.get<SelectedStrategyVersion>(`${base}/validations/${id}/selected-version`, exact)).data;},
    async activateSelectedVersion(id: string) {return (await apiClient.post<SelectedStrategyVersion>(`${base}/validations/${id}/selected-version/activate`, undefined, exact)).data;},
    async policy(source: string) {return (await apiClient.get<ValidationPolicy>(`${base}/${source}/validation-policy`, exact)).data;},
    async list(source: string) {return (await apiClient.get<ValidationSummary[]>(`${base}/${source}/validations`)).data;},
    async get(id: string) {return (await apiClient.get<ValidationView>(`${base}/validations/${id}`, exact)).data;},
    async create(source: string, operation: string) {return (await apiClient.post<ValidationView>(`${base}/${source}/validations/operations/${operation}`, undefined, exact)).data;},
    async cancel(id: string) {return (await apiClient.post<ValidationView>(`${base}/validations/${id}/cancel`, undefined, exact)).data;},
    async holdout(id: string, view: string) {return (await apiClient.post<ValidationView>(`${base}/validations/${id}/holdout-views/${view}`, undefined, exact)).data;},
};
