import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {paperTradingQueryKeys} from '../../src/api/query-keys';
import {useAuthStore} from '../../src/store/auth-store';
import {evaluationWindow, parseObservationResponse, requireEvaluationScope, observationsApi,
    frozenSimCostRows, type FrozenSimCostAssumptions, type ForwardEvaluation, type ObservationDetail, type ObservationReviewDetail} from '../../src/features/paper-trading/api/observations';
import {readObservationOperation, storeObservationOperation, executeObservationOperation, requireObservationReceipt,
    type PendingObservationOperation} from '../../src/features/paper-trading/observation-operations';
import type {SimAnalysisRun} from '../../src/features/paper-trading/api/analysis';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

const id = '01000000-0000-4000-8000-000000000001';
const reviewId = '01000000-0000-4000-8000-000000000002';
const operationId = '01000000-0000-4000-8000-000000000003';
const run = {paperRunId: 'sim-1', canonicalAccountId: 7, strategyVersionId: 'sv-1', publishId: 'pub-1'} as SimAnalysisRun;
const window = {from: '2026-10-01T00:00:00Z', to: '2026-10-02T00:00:00Z', asOf: '2026-10-03T00:00:00Z'};
function evaluation(): ForwardEvaluation {
    return {...run, timeBasis: 'ACTUAL_EVENT_TIME', observationFrom: window.from, observationTo: window.to, observedAt: window.asOf,
        checksum: 'a'.repeat(64), performance: {paperRunId: run.paperRunId, timeBasis: 'ACTUAL_EVENT_TIME'},
        samples: {expected: 24, observed: 0, missing: 2, unknown: 22, status: 'UNAVAILABLE', gapRanges: [], windowLimited: false},
        metrics: {fees: [{currency: 'USDT', totalFee: null, tradeCount: 1, measuredFeeCount: 0}], orderStatuses: []}, findings: []} as unknown as ForwardEvaluation;
}
const original = (): PendingObservationOperation => ({operationId, paperRunId: run.paperRunId, kind: 'OBSERVATION_REVIEW', parentKey: id,
    input: {expectedRevision: 1, decision: 'STOP_OBSERVATION', reason: '人工研究记录，不控制SIM', observedAt: window.asOf, expectedEvaluationChecksum: 'a'.repeat(64)}});
const result = (): ObservationReviewDetail => ({review: {reviewId, observationId: id, revision: 2, decision: 'STOP_OBSERVATION',
    reason: '人工研究记录，不控制SIM', reviewerUserId: 1, reviewedAt: window.asOf, observedAt: window.asOf, evaluationChecksum: 'a'.repeat(64)}, evaluation: evaluation()});

beforeEach(() => {
    const stored = new Map<string, string>();
    vi.stubGlobal('localStorage', {getItem: (key: string) => stored.get(key) ?? null, setItem: (key: string, value: string) => stored.set(key, value), removeItem: (key: string) => stored.delete(key)});
    vi.stubGlobal('window', new EventTarget());
    vi.stubGlobal('navigator', {locks: {request: async (_key: string, _options: unknown, action: (lock: object | null) => Promise<unknown>) => action({})}});
    useAuthStore.setState({currentUser: {userId: 1} as NonNullable<ReturnType<typeof useAuthStore.getState>['currentUser']>});
});
afterEach(() => {vi.restoreAllMocks(); vi.unstubAllGlobals(); useAuthStore.setState({currentUser: null});});

describe('canonical前向观察的只读范围与精确数据', () => {
    it('冻结截止进入请求和缓存键，不自动写入业务数据', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: evaluation()});
        const post = vi.spyOn(apiClient, 'post');
        const signal = new AbortController().signal;
        await observationsApi.evaluate(run, window, signal);
        expect(get).toHaveBeenCalledWith('/paper-trading/strategy-sim/analysis/runs/sim-1/forward-evaluation', expect.objectContaining({params: window, signal}));
        expect(post).not.toHaveBeenCalled();
        expect(paperTradingQueryKeys.forwardEvaluation(1, 'sim-1', window)).not.toEqual(paperTradingQueryKeys.forwardEvaluation(2, 'sim-1', window));
        expect(paperTradingQueryKeys.forwardEvaluation(1, 'sim-1', window)).not.toEqual(paperTradingQueryKeys.forwardEvaluation(1, 'sim-2', window));
        expect(paperTradingQueryKeys.forwardEvaluation(1, 'sim-1', window)).not.toEqual(paperTradingQueryKeys.forwardEvaluation(1, 'sim-1', {...window, asOf: '2026-10-04T00:00:00Z'}));
    });
    it('缺失费用/收益保留null，金额不丢小数，计数与修订仍是整数', () => {
        expect(parseObservationResponse('{"pnl":-0.000000001,"tradeFrequencyPerDay":1.234567890123456789,"twoWayTurnover":null,"totalFee":null,"revision":2,"expected":24,"samples":[{"equity":9007199254740993.12345678}]}'))
            .toEqual({pnl: '-0.000000001', tradeFrequencyPerDay: '1.234567890123456789', twoWayTurnover: null, totalFee: null, revision: 2, expected: 24, samples: [{equity: '9007199254740993.12345678'}]});
    });
    it('仅展示SIM冻结标量，缺失费率不从方向模型或其他来源补零', () => {
        const costs: FrozenSimCostAssumptions = {label: 'FROZEN_SIM_COST_ASSUMPTIONS', source: 'FROZEN_SIM_RUN_CONFIG',
            status: 'PARTIALLY_AVAILABLE', values: {slippageBps: '0', feeRate: '0.001', currentRate: '0.5'},
            fieldStatuses: {slippageBps: 'AVAILABLE', feeRate: 'UNAVAILABLE'}, feeDisclosure: 'SIM_RECORDED_MODEL_CHARGE_NOT_REAL_EXCHANGE_FEE'};
        expect(frozenSimCostRows(costs).find(row => row.field === 'slippageBps')).toMatchObject({value: '0', status: 'AVAILABLE'});
        expect(frozenSimCostRows(costs).find(row => row.field === 'feeRate')).toMatchObject({value: null, status: 'UNAVAILABLE'});
        expect(frozenSimCostRows(costs).some(row => row.field as string === 'currentRate')).toBe(false);
        expect(frozenSimCostRows(null).every(row => row.value === null && row.status === 'UNAVAILABLE')).toBe(true);
        expect(frozenSimCostRows({...costs, source: 'CURRENT_PROVIDER'} as unknown as FrozenSimCostAssumptions).every(row => row.value === null)).toBe(true);
    });
    it('方法披露保留服务端精确分母和原始/展示样本，零完整日与未知不混用', () => {
        expect(parseObservationResponse('{"methodDetails":{"frequencyElapsedSeconds":90000,"frequencyNaturalDays":1.041666666666666666666666666666667,"completeObservationDays":0,"turnoverEquitySampleCount":null,"turnoverMeanHourlyEquity":9007199254740993.00000001,"rawValuationSampleCount":1200,"displayedSampleCount":1000,"chartDownsampled":true}}'))
            .toEqual({methodDetails: {frequencyElapsedSeconds: 90000, frequencyNaturalDays: '1.041666666666666666666666666666667', completeObservationDays: 0,
                turnoverEquitySampleCount: null, turnoverMeanHourlyEquity: '9007199254740993.00000001', rawValuationSampleCount: 1200, displayedSampleCount: 1000, chartDownsampled: true}});
        expect(requireEvaluationScope(evaluation(), run)).toEqual(evaluation());
    });
    it('错误账户/运行/版本/发布/期间/截止/时间基础均停止消费', () => {
        for (const wrong of [{paperRunId: 'wrong'}, {canonicalAccountId: 8}, {strategyVersionId: 'wrong'}, {publishId: 'wrong'},
            {observationFrom: window.to}, {observedAt: '2026-10-04T00:00:00Z'}, {performance: {...evaluation().performance, timeBasis: 'REPLAY_EVENT_TIME'}}])
            expect(() => requireEvaluationScope({...evaluation(), ...wrong} as ForwardEvaluation, run, window)).toThrow();
    });
    it('不将空/逆序/超90日或非ISO区间转换成有效评价', () => {
        expect(evaluationWindow(window.from, window.to, window.asOf)).toEqual(window);
        expect(evaluationWindow(window.from, window.from, window.asOf)).toBeNull();
        expect(evaluationWindow('2026-01-01T00:00:00Z', window.to, window.asOf)).toBeNull();
        expect(evaluationWindow(window.from, window.to, null)).toBeNull();
        expect(evaluationWindow('1', '2', '3')).toBeNull();
    });
    it('拒绝跨Run/超界历史，冻结复盘checksum不匹配不显示', async () => {
        const get = vi.spyOn(apiClient, 'get');
        get.mockResolvedValueOnce({data: [{...run, paperRunId: 'other'}]});
        await expect(observationsApi.list(run, 0)).rejects.toThrow();
        get.mockResolvedValueOnce({data: Array(21).fill({...run})});
        await expect(observationsApi.list(run, 0)).rejects.toThrow();
        get.mockResolvedValueOnce({data: {...result(), review: {...result().review, evaluationChecksum: 'b'.repeat(64)}}});
        await expect(observationsApi.review(id, reviewId, run)).rejects.toThrow('OBSERVATION_REVIEW_SCOPE_MISMATCH');
    });
});

describe('显式人工复盘原身份恢复', () => {
    it('GET 404保留原UUID，不自动POST或换号', async () => {
        const pending = original(); storeObservationOperation(1, run.paperRunId, pending);
        vi.spyOn(observationsApi, 'receipt').mockRejectedValue({status: 404});
        const post = vi.spyOn(observationsApi, 'saveReview');
        await expect(executeObservationOperation(1, run, pending, false, () => true)).rejects.toEqual({status: 404});
        expect(readObservationOperation(1, run.paperRunId)?.operationId).toBe(operationId);
        expect(post).not.toHaveBeenCalled();
    });
    it('只查询原回执和冻结结果，STOP_OBSERVATION不调用执行或控制API', async () => {
        const pending = original(); storeObservationOperation(1, run.paperRunId, pending);
        vi.spyOn(observationsApi, 'receipt').mockResolvedValue({operationId, kind: 'OBSERVATION_REVIEW', parentKey: id, objectId: reviewId, lookupKey: reviewId});
        const review = vi.spyOn(observationsApi, 'review').mockResolvedValue(result());
        const post = vi.spyOn(apiClient, 'post');
        const response = await executeObservationOperation(1, run, pending, false, () => true);
        expect(response).toEqual(result()); expect(review).toHaveBeenCalledWith(id, reviewId, run);
        expect(post).not.toHaveBeenCalled(); expect(readObservationOperation(1, run.paperRunId)).toBeNull();
    });
    it('重发丢失响应前清除旧拒绝标记，并继续保留相同UUID和输入', async () => {
        const pending = {...original(), rejected: true}; storeObservationOperation(1, run.paperRunId, pending);
        const post = vi.spyOn(observationsApi, 'saveReview').mockRejectedValue(new Error('response lost'));
        await expect(executeObservationOperation(1, run, pending, true, () => true)).rejects.toThrow('response lost');
        expect(post).toHaveBeenCalledWith(id, operationId, pending.input);
        expect(readObservationOperation(1, run.paperRunId)).toMatchObject({operationId, input: pending.input, rejected: false});
    });
    it('跨scope、存储损坏、错误回执与并发标签锁不可用时不写', async () => {
        const pending = original(); storeObservationOperation(1, run.paperRunId, pending);
        const post = vi.spyOn(observationsApi, 'saveReview');
        await expect(executeObservationOperation(1, run, pending, true, () => false)).rejects.toThrow('SCOPE_CHANGED');
        expect(() => requireObservationReceipt({operationId, kind: 'OBSERVATION', parentKey: run.paperRunId, objectId: id, lookupKey: id}, pending)).toThrow();
        vi.stubGlobal('navigator', {locks: {request: async (_key: string, _options: unknown, action: (lock: object | null) => Promise<unknown>) => action(null)}});
        await expect(executeObservationOperation(1, run, pending, true, () => true)).rejects.toThrow('PENDING');
        expect(post).not.toHaveBeenCalled();
        localStorage.setItem('nq:strategy-observation:v1:1:sim-1', JSON.stringify({...pending, input: {...pending.input, expectedEvaluationChecksum: 'bad'}}));
        expect(() => readObservationOperation(1, run.paperRunId)).toThrow('STORAGE_INVALID');
    });
    it('新请求在同一标签页锁内登记，已有原操作不能被覆盖', async () => {
        const pending = original(); storeObservationOperation(1, run.paperRunId, pending);
        const other = {...pending, operationId: '01000000-0000-4000-8000-000000000099'};
        const post = vi.spyOn(observationsApi, 'saveReview');
        await expect(executeObservationOperation(1, run, other, true, () => true, true)).rejects.toThrow('PENDING');
        expect(readObservationOperation(1, run.paperRunId)?.operationId).toBe(pending.operationId);
        expect(post).not.toHaveBeenCalled();
    });
    it('创建结果必须保留原观察类型和替代血缘，错误响应不清除原操作', async () => {
        const pending: PendingObservationOperation = {operationId, paperRunId: run.paperRunId, kind: 'OBSERVATION', parentKey: run.paperRunId,
            input: {...run, validationId: id, selectedTrialId: reviewId, kind: 'FORWARD', observationFrom: window.from, observationTo: window.to}};
        storeObservationOperation(1, run.paperRunId, pending);
        const response = {observation: {...pending.input, observationId: id, supersedesObservationId: null}, reviews: [], hasMoreReviews: false} as ObservationDetail;
        const create = vi.spyOn(observationsApi, 'create');
        for (const wrong of [{kind: 'RETROSPECTIVE'}, {supersedesObservationId: reviewId}]) {
            create.mockResolvedValueOnce({...response, observation: {...response.observation, ...wrong}} as ObservationDetail);
            await expect(executeObservationOperation(1, run, pending, true, () => true)).rejects.toThrow('RESULT_MISMATCH');
            expect(readObservationOperation(1, run.paperRunId)?.operationId).toBe(operationId);
        }
    });
});

it('中英文观察合同与人工决定一致，不产生策略评分文案', () => {
    expect(Object.keys(zh.forwardObservation).sort()).toEqual(Object.keys(en.forwardObservation).sort());
    expect(Object.keys(zh.forwardObservation.decisions).sort()).toEqual(Object.keys(en.forwardObservation.decisions).sort());
    expect(Object.keys(zh.forwardObservation.costFields).sort()).toEqual(Object.keys(en.forwardObservation.costFields).sort());
    expect(zh.forwardObservation.frozenSimCostsMeaning).toContain('不是交易所实付费用');
    expect(zh.forwardObservation.decisionBoundary).toContain('不暂停 SIM');
    expect(en.forwardObservation.boundary).toContain('separate from execution');
});
