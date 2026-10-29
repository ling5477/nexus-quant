import {canWriteResearch} from '@/utils/distribution';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';

export type ResultAction =
    | 'EVALUATE'
    | 'PUBLISH'
    | 'INSTRUMENT_SYNC'
    | 'VALIDATION_REVIEW'
    | 'PAPER_EMERGENCY';
export interface PendingResultAction {
    kind: ResultAction;
    owner: number;
    target: string;
    strategyVersionId?: string;
    recordedAt: number;
    requestId?: string;
}
export const resultActionChanged = 'nq:result-action-changed';
const key = (owner: number, kind: ResultAction) => `nq:result-action:v1:${owner}:${kind}`;
const inFlight = new Set<string>();
export const isResultActionInFlight = (pending: PendingResultAction) =>
    inFlight.has(key(pending.owner, pending.kind));
const invalid = () =>
    Object.assign(new Error('RESULT_RECOVERY_UNAVAILABLE'), {
        status: 0,
        code: 'RESULT_RECOVERY_UNAVAILABLE',
        fieldErrors: [],
    });
function notify() {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(resultActionChanged));
}
/** 仅保留主体、原运行或交易所范围；不保存凭据，也不把本地记录当作服务端回执。 */
export function readResultAction(
    owner: number | undefined,
    kind: ResultAction,
): PendingResultAction | null | 'UNAVAILABLE' {
    if (!owner) return null;
    try {
        const raw = localStorage.getItem(key(owner, kind));
        if (raw === null) return null;
        if (raw.length > 2048) return 'UNAVAILABLE';
        const value = JSON.parse(raw) as PendingResultAction;
        return value?.kind === kind &&
            value.owner === owner &&
            typeof value.target === 'string' &&
            /^[A-Za-z0-9._:-]{1,128}$/.test(value.target) &&
            Number.isSafeInteger(value.recordedAt) &&
            value.recordedAt > 0 &&
            (value.requestId === undefined ||
                /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
                    value.requestId,
                )) &&
            (value.strategyVersionId === undefined ||
                (typeof value.strategyVersionId === 'string' &&
                    /^[A-Za-z0-9._:-]{1,128}$/.test(value.strategyVersionId)))
            ? value
            : 'UNAVAILABLE';
    } catch {
        return 'UNAVAILABLE';
    }
}
/** 只有拿到对应正式响应或确定的请求拒绝才清除；GET失败、404及旧结果不能授权重发。 */
export function clearResultAction(pending: PendingResultAction) {
    const current = readResultAction(pending.owner, pending.kind);
    if (
        useAuthStore.getState().currentUser?.userId !== pending.owner ||
        !current ||
        current === 'UNAVAILABLE' ||
        JSON.stringify(current) !== JSON.stringify(pending)
    )
        throw invalid();
    localStorage.removeItem(key(pending.owner, pending.kind));
    if (readResultAction(pending.owner, pending.kind) !== null) throw invalid();
    notify();
}
/** 跨标签互斥覆盖从登记到响应；刷新中断留下原请求，不能换目标再次写入。 */
export async function submitResultAction<T>(
    kind: ResultAction,
    target: string,
    work: (requestId: string) => Promise<T>,
    strategyVersionId?: string,
): Promise<T> {
    const owner = useAuthStore.getState().currentUser?.userId;
    if (
        !owner ||
        !Number.isSafeInteger(owner) ||
        owner <= 0 ||
        !/^[A-Za-z0-9._:-]{1,128}$/.test(target) ||
        !navigator.locks?.request
    )
        throw invalid();
    return navigator.locks.request(key(owner, kind), {ifAvailable: true}, async (lock) => {
        if (!lock) throw invalid();
        const actor = useAuthStore.getState().currentUser;
        if (
            actor?.userId !== owner ||
            !canWriteResearch(actor.roles) ||
            readResultAction(owner, kind) !== null
        )
            throw invalid();
        let requestId: string;
        try {
            requestId = crypto.randomUUID();
            if (
                !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
                    requestId,
                )
            )
                throw invalid();
        } catch {
            throw invalid();
        }
        const pending: PendingResultAction = {
            kind,
            owner,
            target,
            ...(strategyVersionId ? {strategyVersionId} : {}),
            recordedAt: Date.now(),
            requestId,
        };
        localStorage.setItem(key(owner, kind), JSON.stringify(pending));
        if (JSON.stringify(readResultAction(owner, kind)) !== JSON.stringify(pending))
            throw invalid();
        inFlight.add(key(owner, kind));
        notify();
        try {
            const result = await work(pending.requestId!);
            clearResultAction(pending);
            return result;
        } catch (error) {
            const failure = error as Partial<AppApiError>;
            const status = failure.status;
            // 此拒绝发生在服务端登记前；只认可原POST的精确合同，普通409或查询404仍保留UNKNOWN。
            const syncRejectedBeforeRegistration =
                kind === 'INSTRUMENT_SYNC' &&
                status === 409 &&
                failure.code === 'STATE_CONFLICT' &&
                failure.message === 'INSTRUMENT_CATALOG_SYNC_DISABLED' &&
                failure.method === 'POST' &&
                [
                    `/api/instruments/sync-requests/${pending.requestId}`,
                    `/instruments/sync-requests/${pending.requestId}`,
                ].includes(failure.path ?? '');
            const [reviewCaseId, reviewAction] = pending.target.split(':');
            // 只有原复核 POST 的明确事务拒绝才释放身份；查询404和幂等冲突仍不能证明未执行。
            const reviewRejectedBeforeMutation =
                kind === 'VALIDATION_REVIEW' &&
                failure.method === 'POST' &&
                [
                    `/api/validation-review-cases/${reviewCaseId}/${reviewAction}`,
                    `/validation-review-cases/${reviewCaseId}/${reviewAction}`,
                ].includes(failure.path ?? '') &&
                ([400, 401, 403, 404, 422].includes(status ?? 0) ||
                    (status === 409 &&
                        [
                            'REVIEW_CASE_VERSION_CONFLICT',
                            'REVIEW_STATE_TRANSITION_INVALID',
                            'REVIEW_CASE_TERMINAL_STATE_LOCKED',
                        ].includes(failure.code ?? '')));
            if (
                ((kind === 'PUBLISH' && [400, 401, 403, 404, 409, 422].includes(status ?? 0)) ||
                    syncRejectedBeforeRegistration ||
                    reviewRejectedBeforeMutation) &&
                useAuthStore.getState().currentUser?.userId === owner
            )
                clearResultAction(pending);
            throw error;
        } finally {
            inFlight.delete(key(owner, kind));
            notify();
        }
    });
}

/** 原请求仍在发送时不并发确认；其他标签也必须取得同一主体的恢复锁。 */
export async function queryResultAction<T>(
    pending: PendingResultAction,
    read: () => Promise<T>,
): Promise<T> {
    if (!navigator.locks?.request) throw invalid();
    return navigator.locks.request(
        key(pending.owner, pending.kind),
        {ifAvailable: true},
        async (lock) => {
            if (
                !lock ||
                useAuthStore.getState().currentUser?.userId !== pending.owner ||
                JSON.stringify(readResultAction(pending.owner, pending.kind)) !==
                    JSON.stringify(pending)
            )
                throw invalid();
            return read();
        },
    );
}

export interface OriginalResultReceipt<T> {
    requestId: string;
    target: string;
    state: 'PENDING' | 'SUCCEEDED' | 'FAILED';
    createdAt: string;
    completedAt: string | null;
    result: T | null;
}
/** 仅接受原UUID、原范围及正式终态；PENDING及不匹配响应继续保持未知。 */
export function originalResult<T>(
    receipt: OriginalResultReceipt<T>,
    requestId: string,
    target?: string,
): T {
    if (
        !receipt ||
        receipt.requestId !== requestId ||
        (target !== undefined && receipt.target !== target) ||
        !['SUCCEEDED', 'FAILED'].includes(receipt.state) ||
        !receipt.result ||
        !receipt.completedAt
    )
        throw invalid();
    return receipt.result;
}
