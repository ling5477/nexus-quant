import {evaluationWindow, requireEvaluationScope, sameObservationTime, type EvaluationWindow, type Observation, type ForwardEvaluation} from './api/observations';
import type {SimAnalysisRun} from './api/analysis';

type Target = Pick<Observation, 'observationId' | 'observationFrom' | 'observationTo'>;

/** 日期必须是真实UTC自然日；选定目标只允许完整包含的日窗，不裁剪成事件子区间。 */
export function dailyEvidenceWindow(date: string, asOf: string, target?: Target): EvaluationWindow | null {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;
    const start = Date.parse(date + 'T00:00:00Z');
    if (!Number.isFinite(start) || new Date(start).toISOString().slice(0, 10) !== date) return null;
    const from = new Date(start).toISOString(), to = new Date(start + 86400000).toISOString();
    if (target && (start < Date.parse(target.observationFrom) || start + 86400000 > Date.parse(target.observationTo))) return null;
    return evaluationWindow(from, to, asOf);
}

export function firstEvidenceDate(from: string) {
    const time = Date.parse(from);
    return Number.isFinite(time) ? new Date(Math.ceil(time / 86400000) * 86400000).toISOString().slice(0, 10) : '';
}

/** 只封装原响应和读取上下文，不重算金额、改变原JSON词法或持久化新的绩效事实。 */
export function dailyEvidenceExport(run: SimAnalysisRun, owner: number, window: EvaluationWindow,
    source: {evaluation: ForwardEvaluation; rawJson: string; receivedAt: string}, target?: Target) {
    requireEvaluationScope(source.evaluation, run, window);
    const day = dailyEvidenceWindow(window.from.slice(0, 10), window.asOf, target);
    if (!day || !sameObservationTime(day.from, window.from) || !sameObservationTime(day.to, window.to))
        throw new Error('INVALID_DAILY_EVIDENCE_WINDOW');
    const context = {ownerUserId: owner, paperRunId: run.paperRunId, canonicalAccountId: run.canonicalAccountId,
        strategyVersionId: run.strategyVersionId, publishId: run.publishId, observationId: target?.observationId ?? null,
        targetFrom: target?.observationFrom ?? null, targetTo: target?.observationTo ?? null,
        from: window.from, to: window.to, asOf: window.asOf, receivedAt: source.receivedAt,
        methodVersion: source.evaluation.methodVersion, checksum: source.evaluation.checksum};
    return `{"kind":"CANONICAL_SIM_DAILY_EVIDENCE","context":${JSON.stringify(context)},"evaluation":${source.rawJson}}`;
}
