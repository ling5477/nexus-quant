import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {paperTradingQueryKeys} from '../../src/api/query-keys';
import {closedBarObservationMethod, eventObservationMethod, observationMethod, observationsApi, parseObservationResponse,
    requireEvaluationScope, type ForwardEvaluation} from '../../src/features/paper-trading/api/observations';
import {dailyEvidenceExport} from '../../src/features/paper-trading/daily-evidence';
import type {SimAnalysisRun} from '../../src/features/paper-trading/api/analysis';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

const run = {paperRunId: 'sim-closed', canonicalAccountId: 17, strategyVersionId: 'sv-closed', publishId: 'pub-closed'} as SimAnalysisRun;
const window = {from: '2026-11-01T00:00:00Z', to: '2026-11-02T00:00:00Z', asOf: '2026-11-02T00:15:00.000001Z'};
function result(): ForwardEvaluation {
    return {...run, timeBasis: 'ACTUAL_EVENT_TIME', observationFrom: window.from, observationTo: window.to, observedAt: window.asOf,
        methodVersion: closedBarObservationMethod, checksum: 'a'.repeat(64), status: 'UNAVAILABLE', reason: 'MISSING_END_PRICE',
        performance: {paperRunId: run.paperRunId, timeBasis: 'ACTUAL_EVENT_TIME', pnl: null},
        samples: {gapRanges: []}, metrics: {fees: [], orderStatuses: []}, findings: [],
        methodDetails: {closedBarValuation: {status: 'UNAVAILABLE', knowledgeCutoff: window.asOf, priceDigest: 'b'.repeat(64), prices: []},
            benchmarks: {status: 'UNAVAILABLE', reason: 'MISSING_END_PRICE', symbol: 'BTC-USDT', quoteCurrency: 'USDT',
                ...window, priceDigest: 'b'.repeat(64), initialEquity: null,
                cash: {status: 'UNAVAILABLE', returnRatio: null, finalEquity: null, excessReturn: null},
                buyHold: {status: 'UNAVAILABLE', returnRatio: null, finalEquity: null, excessReturn: null}}}} as unknown as ForwardEvaluation;
}
afterEach(() => vi.restoreAllMocks());

describe('显式可信估值与原事件方法隔离', () => {
    it('旧目标和缺省继续V1，未知policy不静默降级', () => {
        expect(observationMethod()).toBe(eventObservationMethod);
        expect(observationMethod({policy: {}})).toBe(eventObservationMethod);
        expect(observationMethod({policy: {methodVersion: eventObservationMethod}})).toBe(eventObservationMethod);
        expect(observationMethod({policy: {methodVersion: closedBarObservationMethod}})).toBe(closedBarObservationMethod);
        expect(observationMethod({policy: {methodVersion: 'FUTURE_UNKNOWN_METHOD'}})).toBeNull();
    });
    it('显式GET包含方法及微秒截止，错方法响应拒绝且不POST', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: result()});
        const post = vi.spyOn(apiClient, 'post');
        const signal = new AbortController().signal;
        expect(await observationsApi.evaluate(run, window, signal, closedBarObservationMethod)).toMatchObject({status: 'UNAVAILABLE'});
        expect(get).toHaveBeenCalledWith(expect.stringContaining('/sim-closed/forward-evaluation'),
            expect.objectContaining({params: {...window, methodVersion: closedBarObservationMethod}, signal}));
        get.mockResolvedValueOnce({data: {...result(), methodVersion: eventObservationMethod}});
        await expect(observationsApi.evaluate(run, window, signal, closedBarObservationMethod)).rejects.toThrow('FORWARD_EVALUATION_SCOPE_MISMATCH');
        expect(post).not.toHaveBeenCalled();
    });
    it('不混用交易对、窗口、知识截止或价格摘要，缺少方法证据不消费', () => {
        const base = result();
        for (const patch of [{symbol: 'ETH-USDT'}, {quoteCurrency: 'BTC'}, {from: window.to}, {to: window.from},
            {asOf: '2026-11-02T00:15:00.000002Z'}, {priceDigest: 'c'.repeat(64)}]) {
            const value = {...base, methodDetails: {...base.methodDetails!, benchmarks: {...base.methodDetails!.benchmarks!, ...patch}}};
            expect(() => requireEvaluationScope(value, run, window, closedBarObservationMethod)).toThrow('FORWARD_BENCHMARK_SCOPE_MISMATCH');
        }
        const valuation = base.methodDetails!.closedBarValuation!;
        for (const patch of [{knowledgeCutoff: '2026-11-02T00:15:00.000002Z'}, {prices: Array(2162).fill({})}])
            expect(() => requireEvaluationScope({...base, methodDetails: {...base.methodDetails!, closedBarValuation: {...valuation, ...patch}}}, run))
                .toThrow('FORWARD_BENCHMARK_SCOPE_MISMATCH');
        expect(() => requireEvaluationScope({...base, methodDetails: null}, run)).toThrow('FORWARD_BENCHMARK_SCOPE_MISMATCH');
    });
    it('基准金额、价格与收益差保持词法精度，未知不填零', () => {
        expect(parseObservationResponse('{"benchmarks":{"initialEquity":9007199254740993.12345678,"cash":{"returnRatio":0,"excessReturn":null},"buyHold":{"excessReturn":-0.000000000000000001}},"prices":[{"price":12345.123456789012345678}],"gridPointCount":25}'))
            .toEqual({benchmarks: {initialEquity: '9007199254740993.12345678', cash: {returnRatio: '0', excessReturn: null},
                buyHold: {excessReturn: '-0.000000000000000001'}}, prices: [{price: '12345.123456789012345678'}], gridPointCount: 25});
    });
    it('每日证据只发对应方法GET，原JSON字节与方法一起下载', async () => {
        const raw = JSON.stringify(result());
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: raw});
        const source = await observationsApi.evaluateEvidence(run, window, undefined, closedBarObservationMethod);
        const download = dailyEvidenceExport(run, 7, window, source);
        expect(download).toContain('"evaluation":' + raw);
        expect(JSON.parse(download).context).toMatchObject({methodVersion: closedBarObservationMethod, asOf: window.asOf});
        expect(get.mock.calls[0][1]?.params).toEqual({...window, methodVersion: closedBarObservationMethod});
        expect(source.evaluation.methodDetails!.benchmarks!.cash.returnRatio).toBeNull();
        const target = {observationId: 'target', observationFrom: window.from, observationTo: window.to};
        expect(() => dailyEvidenceExport(run, 7, window, source, target)).toThrow('FORWARD_EVALUATION_SCOPE_MISMATCH');
        expect(dailyEvidenceExport(run, 7, window, source, {...target, policy: {methodVersion: closedBarObservationMethod}}))
            .toContain('"evaluation":' + raw);
    });
    it('相同owner/session/run/窗口的方法分开缓存，不复用旧法与旧日证据', () => {
        expect(paperTradingQueryKeys.forwardEvaluation(7, run.paperRunId, window, closedBarObservationMethod))
            .not.toEqual(paperTradingQueryKeys.forwardEvaluation(7, run.paperRunId, window, eventObservationMethod));
        expect(paperTradingQueryKeys.dailyEvidence(7, 'session', run.paperRunId, 'obs', window, closedBarObservationMethod))
            .not.toEqual(paperTradingQueryKeys.dailyEvidence(7, 'session', run.paperRunId, 'obs', window, eventObservationMethod));
    });
    it('双语清楚披露成本、小时回撤、未知与历史方法边界', () => {
        expect(Object.keys(zh.forwardValuation).sort()).toEqual(Object.keys(en.forwardValuation).sort());
        expect(zh.forwardValuation.costMeaning).toContain('GROSS_PRICE_RETURN');
        expect(en.forwardValuation.costMeaning).toContain('excluding fees, slippage and executability');
        expect(zh.forwardValuation.boundaryMeaning).toContain('严格早于 T');
        expect(zh.forwardValuation.newMethodMeaning).toContain('不会补写历史');
    });
});
