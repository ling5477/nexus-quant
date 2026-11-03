import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import type {SimAnalysisRun, SimPerformance} from './analysis';

export type ObservationDecision = 'CONTINUE_OBSERVATION' | 'STOP_OBSERVATION' | 'RETURN_TO_RESEARCH';
export const eventObservationMethod = 'CONTINUOUS_SIM_OBSERVATION_V1';
export const closedBarObservationMethod = 'CONTINUOUS_SIM_CLOSED_BAR_VALUATION_V1';
export type ObservationMethod = typeof eventObservationMethod | typeof closedBarObservationMethod;
/** 旧记录缺省继续使用事件方法，未知方法不能降级解释为旧方法。 */
export function observationMethod(observation?: Pick<Observation, 'policy'>): ObservationMethod | null {
    const method = observation?.policy?.methodVersion ?? eventObservationMethod;
    return method === eventObservationMethod || method === closedBarObservationMethod ? method : null;
}
export interface ObservationFinding {code: string; status: string; reason: string | null; from: string | null; to: string | null; count: number | null}
export const simCostAssumptionFields = ['feeRate', 'slippageBps', 'costSource', 'feeAssumptionVersion',
    'slippageAssumptionVersion', 'feeSourceVersion', 'feePaymentPolicy', 'feeExchangeCode', 'feeSymbol',
    'feeObservedAt', 'feeAvailableAt', 'feeSourceSnapshotSha256'] as const;
export interface FrozenSimCostAssumptions {
    label: 'FROZEN_SIM_COST_ASSUMPTIONS'; source: 'FROZEN_SIM_RUN_CONFIG';
    status: 'AVAILABLE' | 'PARTIALLY_AVAILABLE' | 'UNAVAILABLE';
    values: Record<string, string>; fieldStatuses: Record<string, 'AVAILABLE' | 'UNAVAILABLE'>;
    feeDisclosure: 'SIM_RECORDED_MODEL_CHARGE_NOT_REAL_EXCHANGE_FEE';
}
export interface ForwardEvaluationMethodDetails {
    frequencyElapsedSeconds: number | null; frequencyNaturalDays: string | null; frequencyStatus: string;
    completeObservationDays: number | null; completeObservationDaysStatus: string;
    turnoverEquitySampleCount: number | null; turnoverMeanHourlyEquity: string | null; turnoverDefinition: string; turnoverStatus: string;
    rawValuationSampleCount: number | null; displayedSampleCount: number | null; chartDownsampled: boolean | null; valuationSamplesStatus: string;
    closedBarValuation?: ClosedBarValuation;
    benchmarks?: ForwardBenchmarks;
}
export interface ClosedBarValuation {
    status: string; reason: string | null; source: string; priceMethod: string; economicBoundary: string;
    knowledgeCutoff: string; maximumLatenessSeconds: number; collectionMethod: string; maximumClockOffsetSeconds: number;
    gridPointCount: number; priceDigest: string; drawdownSampling: string;
    prices: Array<{valuationBoundary: string; sourceCloseTime: string; availableAt: string; receivedAt: string; barSha256: string; price: string}>;
}
export interface ForwardBenchmark {
    status: string; returnRatio: string | null; finalEquity: string | null; excessReturn: string | null; costAssumption: string;
}
export interface ForwardBenchmarks {
    status: string; reason: string | null; symbol: string; quoteCurrency: string; from: string; to: string; asOf: string;
    priceDigest: string; initialEquity: string | null; cash: ForwardBenchmark; buyHold: ForwardBenchmark;
}
/** 只展示当前SIM实例的冻结白名单；缺失和旧接口均不从当前费率或研究假设推导。 */
export function frozenSimCostRows(cost?: FrozenSimCostAssumptions | null) {
    const trusted = cost?.label === 'FROZEN_SIM_COST_ASSUMPTIONS' && cost.source === 'FROZEN_SIM_RUN_CONFIG';
    return simCostAssumptionFields.map(field => {
        const raw = trusted ? cost.values?.[field] : undefined;
        const known = trusted && cost.fieldStatuses?.[field] === 'AVAILABLE' && typeof raw === 'string'
            && raw.trim().length > 0 && raw.length <= 256;
        return {field, value: known ? raw : null, status: known ? 'AVAILABLE' : 'UNAVAILABLE'};
    });
}
export interface ForwardEvaluation {
    paperRunId: string; canonicalAccountId: number; strategyVersionId: string; publishId: string;
    timeBasis: 'ACTUAL_EVENT_TIME' | 'REPLAY_EVENT_TIME'; observationFrom: string; observationTo: string;
    observedAt: string; dueFrom: string | null; dueTo: string | null; coverageStart: string | null; coverageEnd: string | null;
    watermark: string; methodVersion: string; checksum: string; status: string; reason: string | null;
    samples: {expected: number; observed: number; missing: number; unknown: number; status: string;
        gapRanges: {from: string; to: string}[]; windowLimited: boolean};
    performance: SimPerformance;
    metrics: {tradeCount: number | null; tradeCountStatus: string; tradeFrequencyPerDay: string | null; tradeFrequencyStatus: string;
        twoWayTurnover: string | null; turnoverStatus: string; orderStatuses: {status: string; count: number}[]; orderStatusStatus: string;
        fees: {currency: string | null; totalFee: string | null; tradeCount: number; measuredFeeCount: number}[]; feeStatus: string;
        riskEventCount: number | null; riskStatus: string; executionAnomalyCount: number | null; executionStatus: string;
        feeSource: string; windowLimited: boolean};
    findings: ObservationFinding[];
    costAssumptions?: FrozenSimCostAssumptions | null;
    methodDetails?: ForwardEvaluationMethodDetails | null;
}
export interface Observation {
    observationId: string; paperRunId: string; canonicalAccountId: number; strategyVersionId: string; publishId: string;
    validationId: string; selectedTrialId: string; strategyChecksum: string; kind: 'FORWARD' | 'RETROSPECTIVE';
    observationFrom: string; observationTo: string; timeBasis: string; status: 'OPEN' | 'CLOSED'; revision: number;
    registeredAt: string; supersedesObservationId: string | null; policy: Record<string, unknown>;
}
export interface ObservationReview {
    reviewId: string; observationId: string; revision: number; decision: ObservationDecision; reason: string;
    reviewerUserId: number; reviewedAt: string; observedAt: string; evaluationChecksum: string;
}
export interface ObservationDetail {observation: Observation; reviews: ObservationReview[]; hasMoreReviews: boolean}
export interface ObservationReviewDetail {review: ObservationReview; evaluation: ForwardEvaluation}
export interface ObservationCreateRequest {
    paperRunId: string; canonicalAccountId: number; strategyVersionId: string; publishId: string; validationId: string;
    selectedTrialId: string; kind: 'FORWARD' | 'RETROSPECTIVE'; observationFrom: string; observationTo: string;
    supersedesObservationId?: string;
    methodVersion?: ObservationMethod;
}
export interface ObservationReviewRequest {expectedRevision: number; decision: ObservationDecision; reason: string; observedAt: string; expectedEvaluationChecksum: string}
export interface ObservationReceipt {operationId: string; kind: 'OBSERVATION' | 'OBSERVATION_REVIEW'; parentKey: string; objectId: string; lookupKey: string}
export interface EvaluationWindow {from: string; to: string; asOf: string}
export const observationIdPattern = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;

/** 金额沿用canonical词法精度；样本数、版本及身份不能被统一转成小数。 */
export function parseObservationResponse(text: string): unknown {
    return parseExactJson(text, new Set(['initialEquity', 'finalEquity', 'cash', 'positionQuantity', 'markPrice', 'equity', 'pnl',
        'returnRatio', 'maxDrawdown', 'maxDrawdownRatio', 'value', 'riskFreeRate', 'winRate', 'netProfit', 'netLoss',
        'profitLossRatio', 'tradeFrequencyPerDay', 'twoWayTurnover', 'totalFee', 'fee', 'discountAssetBalance', 'discountAssetQuoteValue',
        'frequencyNaturalDays', 'turnoverMeanHourlyEquity', 'price', 'excessReturn']));
}
const exact = {transformResponse: [parseObservationResponse]};
type RunIdentity = Pick<SimAnalysisRun, 'paperRunId' | 'canonicalAccountId' | 'strategyVersionId' | 'publishId'> & Partial<Pick<SimAnalysisRun, 'symbol'>>;
/** 保留服务端Instant的纳秒身份；Date会截断微秒，不能用来核对固定截止。 */
function instantNanos(value: string | null | undefined): bigint | null {
    const match = typeof value === 'string' && /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/.exec(value);
    if (!match) return null;
    const millis = Date.parse(match[1] + match[3]);
    return Number.isFinite(millis) ? BigInt(millis) * 1_000_000n + BigInt((match[2] ?? '').padEnd(9, '0')) : null;
}
export function sameObservationTime(left: string | null | undefined, right: string | null | undefined) {
    const a = instantNanos(left), b = instantNanos(right);
    return a !== null && b !== null && a === b;
}
export function evaluationWindow(from: string | null, to: string | null, asOf: string | null): EvaluationWindow | null {
    if (!from || !to || !asOf) return null;
    const start = instantNanos(from), end = instantNanos(to), observed = instantNanos(asOf);
    return start !== null && end !== null && observed !== null && end > start
        && end - start <= 90n * 86_400_000_000_000n ? {from, to, asOf} : null;
}
/** 诊断只使用原评价已保存的覆盖端点和同一截止，不以整Run结果补齐缺失边界。 */
export function observationEventWindow(value: ForwardEvaluation): EvaluationWindow | null {
    if (value.timeBasis !== 'ACTUAL_EVENT_TIME') return null;
    const range = evaluationWindow(value.coverageStart, value.coverageEnd, value.observedAt);
    const target = evaluationWindow(value.observationFrom, value.observationTo, value.observedAt);
    if (!range || !target) return null;
    return instantNanos(range.from)! >= instantNanos(target.from)! && instantNanos(range.to)! <= instantNanos(target.to)!
        && instantNanos(range.to)! <= instantNanos(range.asOf)! ? range : null;
}
export function requireObservationScope(value: RunIdentity, run: RunIdentity) {
    if (value.paperRunId !== run.paperRunId || value.canonicalAccountId !== run.canonicalAccountId
        || value.strategyVersionId !== run.strategyVersionId || value.publishId !== run.publishId)
        throw new Error('FORWARD_OBSERVATION_IDENTITY_MISMATCH');
}
export function requireEvaluationScope(value: ForwardEvaluation, run: RunIdentity, window?: EvaluationWindow, method?: ObservationMethod) {
    requireObservationScope(value, run);
    if (!value.performance || value.performance.paperRunId !== run.paperRunId || value.performance.timeBasis !== value.timeBasis
        || !['ACTUAL_EVENT_TIME', 'REPLAY_EVENT_TIME'].includes(value.timeBasis)
        || !value.samples || !Array.isArray(value.samples.gapRanges) || !Array.isArray(value.findings)
        || !value.metrics || !Array.isArray(value.metrics.fees) || !Array.isArray(value.metrics.orderStatuses)
        || !/^[a-f0-9]{64}$/i.test(value.checksum) || (method !== undefined && value.methodVersion !== method)
        || (window && (!sameObservationTime(value.observationFrom, window.from) || !sameObservationTime(value.observationTo, window.to)
            || !sameObservationTime(value.observedAt, window.asOf)))) throw new Error('FORWARD_EVALUATION_SCOPE_MISMATCH');
    if (value.methodVersion === closedBarObservationMethod) {
        const prices = value.methodDetails?.closedBarValuation, benchmark = value.methodDetails?.benchmarks;
        if (!prices || !benchmark || !Array.isArray(prices.prices) || prices.prices.length > 2161
            || !sameObservationTime(prices.knowledgeCutoff, value.observedAt)
            || !sameObservationTime(benchmark.from, value.observationFrom) || !sameObservationTime(benchmark.to, value.observationTo)
            || !sameObservationTime(benchmark.asOf, value.observedAt) || prices.priceDigest !== benchmark.priceDigest
            || benchmark.symbol !== (run.symbol ?? 'BTC-USDT') || benchmark.quoteCurrency !== 'USDT'
            || !benchmark.cash || !benchmark.buyHold)
            throw new Error('FORWARD_BENCHMARK_SCOPE_MISMATCH');
    }
    return value;
}
const root = '/strategy-observations';
export const observationsApi = {
    /** 导出保留原始JSON数值词法；显示解析与下载字节共用一次原GET，避免两次读取水位不同。 */
    async evaluateEvidence(run: RunIdentity, window: EvaluationWindow, signal?: AbortSignal, method?: ObservationMethod) {
        const {data} = await apiClient.get<string>(`/paper-trading/strategy-sim/analysis/runs/${encodeURIComponent(run.paperRunId)}/forward-evaluation`,
            {params: method ? {...window, methodVersion: method} : window, signal, responseType: 'text', transformResponse: [(raw: string, _headers: unknown, status?: number) => {
                // 成功响应保留数值词法；错误响应沿用统一结构化错误码与诊断合同。
                if (status !== undefined && status >= 400) {
                    try {return JSON.parse(raw) as unknown;} catch {return raw;}
                }
                return raw;
            }]});
        if (typeof data !== 'string') throw new Error('INVALID_EVALUATION_EVIDENCE');
        const evaluation = requireEvaluationScope(parseObservationResponse(data) as ForwardEvaluation, run, window, method);
        return {evaluation, rawJson: data, receivedAt: new Date().toISOString()};
    },
    async evaluate(run: RunIdentity, window: EvaluationWindow, signal?: AbortSignal, method?: ObservationMethod) {
        const {data} = await apiClient.get<ForwardEvaluation>(`/paper-trading/strategy-sim/analysis/runs/${encodeURIComponent(run.paperRunId)}/forward-evaluation`,
            {...exact, params: method ? {...window, methodVersion: method} : window, signal});
        return requireEvaluationScope(data, run, window, method);
    },
    async list(run: RunIdentity, offset: number, signal?: AbortSignal) {
        const {data} = await apiClient.get<Observation[]>(root, {...exact, params: {paperRunId: run.paperRunId, limit: 20, offset}, signal});
        if (!Array.isArray(data) || data.length > 20) throw new Error('OBSERVATION_LIST_UNBOUNDED');
        data.forEach(value => requireObservationScope(value, run));
        return data;
    },
    async detail(id: string, run: RunIdentity, signal?: AbortSignal, reviewsOffset = 0) {
        const {data} = await apiClient.get<ObservationDetail>(`${root}/${encodeURIComponent(id)}`, {...exact, params: {reviewsOffset}, signal});
        requireObservationScope(data.observation, run);
        if (data.observation.observationId !== id || !Array.isArray(data.reviews) || data.reviews.length > 20
            || data.reviews.some(review => review.observationId !== id)) throw new Error('OBSERVATION_DETAIL_SCOPE_MISMATCH');
        return data;
    },
    async review(observationId: string, reviewId: string, run: RunIdentity, signal?: AbortSignal) {
        const {data} = await apiClient.get<ObservationReviewDetail>(`${root}/${encodeURIComponent(observationId)}/reviews/${encodeURIComponent(reviewId)}`, {...exact, signal});
        requireEvaluationScope(data.evaluation, run);
        if (data.review.observationId !== observationId || data.review.reviewId !== reviewId
            || data.review.evaluationChecksum !== data.evaluation.checksum || !sameObservationTime(data.review.observedAt, data.evaluation.observedAt))
            throw new Error('OBSERVATION_REVIEW_SCOPE_MISMATCH');
        return data;
    },
    async receipt(id: string) {return (await apiClient.get<ObservationReceipt>(`${root}/operations/${encodeURIComponent(id)}`)).data;},
    async create(id: string, input: ObservationCreateRequest) {return (await apiClient.post<ObservationDetail>(`${root}/operations/${encodeURIComponent(id)}`, input, exact)).data;},
    async saveReview(observationId: string, id: string, input: ObservationReviewRequest) {
        return (await apiClient.post<ObservationReviewDetail>(`${root}/${encodeURIComponent(observationId)}/reviews/operations/${encodeURIComponent(id)}`, input, exact)).data;
    },
};
