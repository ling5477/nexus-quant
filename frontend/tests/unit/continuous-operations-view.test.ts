import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {continuousJobs, continuousPollFreshness} from '../../src/features/paper-trading/continuous-operations-view';
import {dailyEvidenceExport, dailyEvidenceWindow, firstEvidenceDate} from '../../src/features/paper-trading/daily-evidence';
import {observationsApi, type ForwardEvaluation} from '../../src/features/paper-trading/api/observations';
import {paperTradingQueryKeys} from '../../src/api/query-keys';
import type {ContinuousSimStatus} from '../../src/features/paper-trading/types/paper-trading';
import type {ScheduledJobControl} from '../../src/features/scheduler/types';
import type {SimAnalysisRun} from '../../src/features/paper-trading/api/analysis';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

const now = Date.parse('2026-11-03T01:00:00Z');
const status = {status: 'RUNNING', lastPollAt: '2026-11-03T00:49:00Z', lastSuccessfulPollAt: '2026-11-03T00:49:00Z',
    blockReason: 'WAITING_FOR_EXECUTION_QUOTE', consecutivePollFailures: 0} as ContinuousSimStatus;
const job = {jobKey: 'CONTINUOUS_SIM_POLL', enabled: false, fixedDelayMs: 300000, lastStatus: 'SUCCESS',
    consecutiveFailures: 0, lastStartedAt: null, lastFinishedAt: null, nextRunAt: null} as ScheduledJobControl;
const run = {paperRunId: 'sim-1', canonicalAccountId: 7, strategyVersionId: 'sv-1', publishId: 'pub-1'} as SimAnalysisRun;
const cutoff = '2026-11-01T09:15:05.667317Z';
const range = dailyEvidenceWindow('2026-10-31', cutoff)!;
const raw = JSON.stringify({...run, timeBasis: 'ACTUAL_EVENT_TIME', observationFrom: range.from, observationTo: range.to,
    observedAt: cutoff, status: 'NOT_COMPARABLE', reason: 'OBSERVATION_BOUNDARY_MARK_UNAVAILABLE',
    checksum: 'a'.repeat(64), methodVersion: 'CONTINUOUS_SIM_OBSERVATION_V1',
    performance: {paperRunId: run.paperRunId, timeBasis: 'ACTUAL_EVENT_TIME', pnl: null, finalEquity: 'RAW_DECIMAL'},
    samples: {gapRanges: []}, findings: [], metrics: {fees: [], orderStatuses: []}}).replace('"RAW_DECIMAL"', '9007199254740993.12345678');

afterEach(() => vi.restoreAllMocks());

describe('持续SIM运营解释不替代原状态', () => {
    it('job SUCCESS和OFF不能覆盖STALLED、失败次数或报价等待', () => {
        const stalled = {...status, status: 'STALLED', consecutivePollFailures: 3};
        const rows = continuousJobs([job]);
        expect(rows.get(job.jobKey)).toMatchObject({lastStatus: 'SUCCESS', enabled: false});
        expect(continuousPollFreshness(stalled, job.fixedDelayMs, now)).toBe('RECENT');
        expect(stalled).toMatchObject({status: 'STALLED', consecutivePollFailures: 3});
        expect(status.blockReason).toBe('WAITING_FOR_EXECUTION_QUOTE');
    });
    it('新鲜度以有效周期检查，边界和调整周期不改变游标', () => {
        expect(continuousPollFreshness(status, 300000, now)).toBe('RECENT');
        expect(continuousPollFreshness(status, 300000, now + 1)).toBe('STALE');
        expect(continuousPollFreshness(status, 600000, now + 1)).toBe('RECENT');
        expect(continuousPollFreshness({...status, status: 'STOPPED'}, 300000, now)).toBe('PAUSED');
    });
    it('无成功轮询、错误时间、缺配置、未知job不默认健康或关闭', () => {
        expect(continuousJobs([]).get('PAPER_MATCHING')).toBeUndefined();
        for (const delay of [null, -1, 0, 29999, NaN, 86400001]) expect(continuousPollFreshness(status, delay, now)).toBe('UNKNOWN');
        expect(continuousPollFreshness({...status, lastSuccessfulPollAt: null}, 300000, now)).toBe('UNKNOWN');
        expect(continuousPollFreshness({...status, lastPollAt: 'invalid'}, 300000, now)).toBe('UNKNOWN');
        expect(continuousPollFreshness({...status, lastPollAt: '2026-11-04T00:00:00Z'}, 300000, now)).toBe('UNKNOWN');
        expect(() => continuousJobs([job, job])).toThrow();
        expect(() => continuousJobs([{...job, enabled: undefined} as unknown as ScheduledJobControl])).toThrow();
    });
});

describe('UTC每日证据保留原评价与时间精度', () => {
    it('日期严格校验并仅允许完整包含的UTC日，不拼接部分观察', () => {
        const target = {observationId: 'obs', observationFrom: '2026-10-31T09:00:00Z', observationTo: '2026-11-02T09:00:00Z'};
        expect(dailyEvidenceWindow('2026-10-31', cutoff, target)).toBeNull();
        expect(dailyEvidenceWindow('2026-11-02', cutoff, target)).toBeNull();
        expect(dailyEvidenceWindow('2026-11-01', cutoff, target)).toMatchObject({from: '2026-11-01T00:00:00.000Z', to: '2026-11-02T00:00:00.000Z', asOf: cutoff});
        for (const date of ['2026-02-30', '2026-11-31', '2026-13-01', '', '2026-1-1']) expect(dailyEvidenceWindow(date, cutoff)).toBeNull();
        expect(firstEvidenceDate(target.observationFrom)).toBe('2026-11-01');
        expect(dailyEvidenceWindow('2026-11-01', 'bad')).toBeNull();
    });
    it('未到期日仍查询完整24小时，由原服务端返回不足状态，不裁剪到now', () => {
        expect(dailyEvidenceWindow('2026-11-01', cutoff)).toMatchObject({to: '2026-11-02T00:00:00.000Z', asOf: cutoff});
    });
    it('同一次GET保留数值词法、微秒、null及NOT_COMPARABLE，导出不写经济或研究事实', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: raw});
        const post = vi.spyOn(apiClient, 'post');
        const signal = new AbortController().signal;
        const source = await observationsApi.evaluateEvidence(run, range, signal);
        expect(source.evaluation.performance.finalEquity).toBe('9007199254740993.12345678');
        const exported = dailyEvidenceExport(run, 1, range, source);
        expect(exported).toContain('"evaluation":' + raw);
        expect(JSON.parse(exported).context).toMatchObject({ownerUserId: 1, asOf: cutoff, observationId: null});
        expect(JSON.parse(exported).evaluation).toMatchObject({status: 'NOT_COMPARABLE', performance: {pnl: null}});
        expect(get).toHaveBeenCalledWith(expect.stringContaining('/sim-1/forward-evaluation'), expect.objectContaining({params: range, signal, responseType: 'text'}));
        expect(post).not.toHaveBeenCalled();
    });
    it('串运行/账户/方法时间截止与无权读取不能成为可导出的响应', async () => {
        const get = vi.spyOn(apiClient, 'get');
        for (const [field, value] of [['paperRunId','other'],['canonicalAccountId',8],['strategyVersionId','wrong'],['publishId','wrong'],['observedAt',cutoff.replace('317','318')]] as const) {
            get.mockResolvedValueOnce({data: JSON.stringify({...JSON.parse(raw), [field]: value})});
            await expect(observationsApi.evaluateEvidence(run, range)).rejects.toThrow();
        }
        get.mockRejectedValueOnce({status: 404});
        await expect(observationsApi.evaluateEvidence(run, range)).rejects.toMatchObject({status: 404});
    });
    it('owner、session、run、观察及日窗口均隔离缓存键', () => {
        const key = paperTradingQueryKeys.dailyEvidence(1, 'session-a', run.paperRunId, 'obs', range);
        for (const different of [paperTradingQueryKeys.dailyEvidence(2, 'session-a', run.paperRunId, 'obs', range),
            paperTradingQueryKeys.dailyEvidence(1, 'session-b', run.paperRunId, 'obs', range),
            paperTradingQueryKeys.dailyEvidence(1, 'session-a', 'other', 'obs', range),
            paperTradingQueryKeys.dailyEvidence(1, 'session-a', run.paperRunId, 'other', range),
            paperTradingQueryKeys.dailyEvidence(1, 'session-a', run.paperRunId, 'obs', {...range, asOf: cutoff.replace('317','318')})]) expect(different).not.toEqual(key);
    });
    it('双语保留不可用、共享和原始响应的产品说明', () => {
        for (const locale of [zh, en]) {
            expect(locale.continuousOperationsView.jobs.PAPER_MATCHING).toBeTruthy();
            expect(locale.continuousOperationsView.freshness.UNKNOWN).toBeTruthy();
            expect(locale.dailySimEvidence.meaning).toBeTruthy();
            expect(locale.dailySimEvidence.invalid).toBeTruthy();
        }
    });
});
