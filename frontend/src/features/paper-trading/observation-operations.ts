import {observationsApi, observationIdPattern, requireEvaluationScope, requireObservationScope,
    type ObservationCreateRequest, type ObservationReviewRequest, type ObservationReceipt} from './api/observations';
import type {SimAnalysisRun} from './api/analysis';
import {useAuthStore} from '@/store/auth-store';

export type PendingObservationOperation = {
    operationId: string; paperRunId: string; rejected?: boolean;
} & ({kind: 'OBSERVATION'; parentKey: string; input: ObservationCreateRequest}
    | {kind: 'OBSERVATION_REVIEW'; parentKey: string; input: ObservationReviewRequest});
export const observationOperationChanged = 'nq:strategy-observation-changed';
const key = (owner: number, run: string) => `nq:strategy-observation:v1:${owner}:${run}`;
export function readObservationOperation(owner: number, run: string): PendingObservationOperation | null {
    const raw = localStorage.getItem(key(owner, run));
    if (!raw) return null;
    const value = JSON.parse(raw) as PendingObservationOperation;
    if (!observationIdPattern.test(value.operationId) || value.paperRunId !== run || !value.input
        || typeof value.input !== 'object' || Array.isArray(value.input)
        || (value.kind !== 'OBSERVATION' && value.kind !== 'OBSERVATION_REVIEW')
        || (value.kind === 'OBSERVATION' && (value.parentKey !== run || value.input.paperRunId !== run))
        || (value.kind === 'OBSERVATION_REVIEW' && (!observationIdPattern.test(value.parentKey)
            || !Number.isInteger(value.input.expectedRevision) || value.input.expectedRevision < 0
            || !['CONTINUE_OBSERVATION', 'STOP_OBSERVATION', 'RETURN_TO_RESEARCH'].includes(value.input.decision)
            || typeof value.input.reason !== 'string' || !value.input.reason.trim() || value.input.reason.length > 2000
            || !Number.isFinite(Date.parse(value.input.observedAt)) || !/^[a-f0-9]{64}$/i.test(value.input.expectedEvaluationChecksum))))
        throw new Error('OBSERVATION_OPERATION_STORAGE_INVALID');
    return value;
}
export function storeObservationOperation(owner: number, run: string, value: PendingObservationOperation) {
    localStorage.setItem(key(owner, run), JSON.stringify(value));
    window.dispatchEvent(new Event(observationOperationChanged));
}
export function clearObservationOperation(owner: number, run: string, operationId: string) {
    if (readObservationOperation(owner, run)?.operationId !== operationId) throw new Error('OBSERVATION_OPERATION_CHANGED');
    localStorage.removeItem(key(owner, run));
    window.dispatchEvent(new Event(observationOperationChanged));
}
export function requireObservationReceipt(value: ObservationReceipt, original: PendingObservationOperation) {
    if (value.operationId !== original.operationId || value.kind !== original.kind || value.parentKey !== original.parentKey
        || !observationIdPattern.test(value.objectId)) throw new Error('OBSERVATION_OPERATION_RECEIPT_MISMATCH');
    return value;
}

/** 恢复只能显式查询或重发已保存的同一请求；404不表示旧请求没有执行。 */
export async function executeObservationOperation(owner: number, run: SimAnalysisRun, original: PendingObservationOperation,
    post: boolean, current: () => boolean, reserve = false) {
    if (!navigator.locks) throw new Error('OBSERVATION_OPERATION_COORDINATION_UNAVAILABLE');
    return navigator.locks.request(key(owner, run.paperRunId), {ifAvailable: true}, async lock => {
        if (!lock) throw new Error('OBSERVATION_OPERATION_PENDING');
        // 新请求的检查与保存也在同一标签页锁内，不能让并发标签在首个POST途中覆盖原UUID。
        if (!current() || useAuthStore.getState().currentUser?.userId !== owner) throw new Error('OBSERVATION_OPERATION_SCOPE_CHANGED');
        if (reserve) {
            if (readObservationOperation(owner, run.paperRunId)) throw new Error('OBSERVATION_OPERATION_PENDING');
            storeObservationOperation(owner, run.paperRunId, original);
        }
        const saved = readObservationOperation(owner, run.paperRunId);
        if (!current() || useAuthStore.getState().currentUser?.userId !== owner || saved?.operationId !== original.operationId
            || JSON.stringify(saved.input) !== JSON.stringify(original.input)) throw new Error('OBSERVATION_OPERATION_SCOPE_CHANGED');
        let result;
        if (post) {
            // 明确拒绝后再次提交仍可能丢失响应，发送前清除旧拒绝标记，不能据旧标记放弃未知结果。
            storeObservationOperation(owner, run.paperRunId, {...original, rejected: false});
            result = original.kind === 'OBSERVATION'
                ? await observationsApi.create(original.operationId, original.input)
                : await observationsApi.saveReview(original.parentKey, original.operationId, original.input);
        } else {
            const receipt = requireObservationReceipt(await observationsApi.receipt(original.operationId), original);
            result = original.kind === 'OBSERVATION'
                ? await observationsApi.detail(receipt.objectId, run)
                : await observationsApi.review(original.parentKey, receipt.objectId, run);
        }
        if ('observation' in result) {
            requireObservationScope(result.observation, run);
            if (original.kind !== 'OBSERVATION' || result.observation.validationId !== original.input.validationId
                || result.observation.selectedTrialId !== original.input.selectedTrialId
                || result.observation.kind !== original.input.kind
                || result.observation.supersedesObservationId !== (original.input.supersedesObservationId ?? null)
                || result.observation.observationFrom !== original.input.observationFrom
                    && Date.parse(result.observation.observationFrom) !== Date.parse(original.input.observationFrom)
                || result.observation.observationTo !== original.input.observationTo
                    && Date.parse(result.observation.observationTo) !== Date.parse(original.input.observationTo))
                throw new Error('OBSERVATION_OPERATION_RESULT_MISMATCH');
        } else {
            requireEvaluationScope(result.evaluation, run);
            if (original.kind !== 'OBSERVATION_REVIEW' || result.review.observationId !== original.parentKey
                || result.review.evaluationChecksum !== original.input.expectedEvaluationChecksum
                || result.review.evaluationChecksum !== result.evaluation.checksum
                || Date.parse(result.review.observedAt) !== Date.parse(original.input.observedAt)
                || result.review.decision !== original.input.decision || result.review.reason !== original.input.reason)
                throw new Error('OBSERVATION_OPERATION_RESULT_MISMATCH');
        }
        if (!current() || useAuthStore.getState().currentUser?.userId !== owner) throw new Error('OBSERVATION_OPERATION_SCOPE_CHANGED');
        clearObservationOperation(owner, run.paperRunId, original.operationId);
        return result;
    });
}
