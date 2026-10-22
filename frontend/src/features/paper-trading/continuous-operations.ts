import {canWriteResearch} from '@/utils/distribution';
import {apiClient} from '@/api/client';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {ContinuousSimStatus} from './types/paper-trading';
import {parseContinuousSimStatus} from './continuous-state';

export type ContinuousOperationInput =
    | {type: 'CREATE'; publishId: string; budget: string}
    | {type: 'PAUSE' | 'RESUME'; paperRunId: string};
export type ContinuousOperationRequest = ContinuousOperationInput & {operationId: string};
export interface PendingContinuousOperation {
    ownerUserId: number;
    request: ContinuousOperationRequest;
    phase: 'SUBMITTING' | 'UNKNOWN';
    recordedAt: number;
}
export type ContinuousOperationStorage =
    | {kind: 'none'}
    | {kind: 'unavailable'}
    | {kind: 'pending'; pending: PendingContinuousOperation};
export interface ConfirmedContinuousOperation {
    ownerUserId: number;
    operationId: string;
    type: ContinuousOperationInput['type'];
    publishId: string | null;
    budget: string | null;
    paperRunId: string;
    status: 'COMPLETED';
    result: ContinuousSimStatus;
    completedAt: string;
}

export const continuousOperationChanged = 'nq:continuous-operation-changed';
const key = (owner: number) => `nq:continuous-operation:v1:${owner}`;
const inFlight = new Set<string>();
const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/;
const identity = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
type Session = {owner: number; token: string};
let missingResult: {owner: number; input: string; token: string} | null = null;

function error(code: string): AppApiError {
    return Object.assign(new Error(code), {name: 'AppApiError', status: 0, code, fieldErrors: []});
}
function notify() {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(continuousOperationChanged));
}

/** 预算只用十进制字符串和整数比较；不经浮点数舍入，也不把微小正值显示为零。 */
export function continuousBudget(value: unknown): string | null {
    if (typeof value !== 'string' || value.length > 32 || !/^\d+(?:\.\d{1,8})?$/.test(value))
        return null;
    const [whole, fractional = ''] = value.split('.');
    const scaled = BigInt(whole) * 100_000_000n + BigInt(fractional.padEnd(8, '0'));
    if (scaled <= 0n || scaled > 10_000_000_000_000n) return null;
    const fraction = fractional.replace(/0+$/, '');
    return BigInt(whole).toString() + (fraction ? `.${fraction}` : '');
}

export function validContinuousOperationRequest(
    value: unknown,
): value is ContinuousOperationRequest {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
    const row = value as Record<string, unknown>;
    if (typeof row.operationId !== 'string' || !uuid.test(row.operationId)) return false;
    if (row.type === 'CREATE')
        return (
            Object.keys(row).every((field) =>
                ['operationId', 'type', 'publishId', 'budget'].includes(field),
            ) &&
            typeof row.publishId === 'string' &&
            identity.test(row.publishId) &&
            continuousBudget(row.budget) !== null
        );
    return (
        (row.type === 'PAUSE' || row.type === 'RESUME') &&
        Object.keys(row).every((field) => ['operationId', 'type', 'paperRunId'].includes(field)) &&
        typeof row.paperRunId === 'string' &&
        identity.test(row.paperRunId)
    );
}

function inputKey(request: ContinuousOperationRequest): string {
    return JSON.stringify(
        request.type === 'CREATE'
            ? [
                  request.operationId,
                  request.type,
                  request.publishId,
                  continuousBudget(request.budget),
              ]
            : [request.operationId, request.type, request.paperRunId],
    );
}
function same(a: PendingContinuousOperation, b: PendingContinuousOperation): boolean {
    return (
        a.ownerUserId === b.ownerUserId &&
        a.recordedAt === b.recordedAt &&
        inputKey(a.request) === inputKey(b.request)
    );
}

/** 刷新遗留的提交中记录只能视为未知；损坏或不可读的存储不能当作没有待确认命令。 */
export function readContinuousOperation(owner?: number): ContinuousOperationStorage {
    if (!owner || !Number.isSafeInteger(owner) || owner <= 0) return {kind: 'none'};
    try {
        const raw = localStorage.getItem(key(owner));
        if (raw === null) return {kind: 'none'};
        if (raw.length > 8192) return {kind: 'unavailable'};
        const value = JSON.parse(raw) as PendingContinuousOperation;
        if (
            !value ||
            value.ownerUserId !== owner ||
            !['SUBMITTING', 'UNKNOWN'].includes(value.phase) ||
            !Number.isSafeInteger(value.recordedAt) ||
            value.recordedAt <= 0 ||
            Object.keys(value).some(
                (field) => !['ownerUserId', 'request', 'phase', 'recordedAt'].includes(field),
            ) ||
            !validContinuousOperationRequest(value.request)
        )
            return {kind: 'unavailable'};
        return {
            kind: 'pending',
            pending:
                value.phase === 'SUBMITTING' && !inFlight.has(value.request.operationId)
                    ? {...value, phase: 'UNKNOWN'}
                    : value,
        };
    } catch {
        return {kind: 'unavailable'};
    }
}

export function canOperateContinuous(): boolean {
    const user = useAuthStore.getState().currentUser;
    return Boolean(
        user?.authenticated &&
            user.userId > 0 &&
            canWriteResearch(user.roles),
    );
}
function session(write: boolean, pending?: PendingContinuousOperation): Session {
    const authState = useAuthStore.getState();
    const user = authState.currentUser;
    const token = selectSessionIdentity(authState);
    if (
        !user?.authenticated ||
        !Number.isSafeInteger(user.userId) ||
        user.userId <= 0 ||
        !token ||
        (write && !canOperateContinuous())
    )
        throw error('CONTINUOUS_OPERATION_PERMISSION');
    if (pending && pending.ownerUserId !== user.userId)
        throw error('CONTINUOUS_OPERATION_SCOPE_CHANGED');
    return {owner: user.userId, token};
}
function requireSession(original: Session, write: boolean) {
    const current = session(write);
    if (current.owner !== original.owner || current.token !== original.token)
        throw error('CONTINUOUS_OPERATION_SCOPE_CHANGED');
}
function requireOriginal(pending: PendingContinuousOperation) {
    const stored = readContinuousOperation(pending.ownerUserId);
    if (stored.kind !== 'pending' || !same(stored.pending, pending))
        throw error('CONTINUOUS_OPERATION_STORAGE_UNAVAILABLE');
}
function retain(pending: PendingContinuousOperation, initial = false) {
    if (initial) {
        if (readContinuousOperation(pending.ownerUserId).kind !== 'none')
            throw error('CONTINUOUS_OPERATION_PENDING');
    } else requireOriginal(pending);
    try {
        localStorage.setItem(key(pending.ownerUserId), JSON.stringify(pending));
        requireOriginal(pending);
    } catch {
        throw error('CONTINUOUS_OPERATION_STORAGE_UNAVAILABLE');
    }
    notify();
}
function clear(pending: PendingContinuousOperation) {
    requireOriginal(pending);
    try {
        localStorage.removeItem(key(pending.ownerUserId));
        if (readContinuousOperation(pending.ownerUserId).kind !== 'none')
            throw new Error('not removed');
    } catch {
        throw error('CONTINUOUS_OPERATION_STORAGE_UNAVAILABLE');
    }
    notify();
}

/** 同主体跨标签持锁后才保存/发送；协调不可用时关闭写入，不退回会覆盖原编号的实现。 */
async function coordinated<T>(owner: number, work: () => Promise<T>): Promise<T> {
    if (typeof navigator === 'undefined' || !navigator.locks?.request)
        throw error('CONTINUOUS_OPERATION_COORDINATION_UNAVAILABLE');
    return navigator.locks.request(
        key(owner),
        {mode: 'exclusive', ifAvailable: true},
        async (lock) => {
            if (!lock) throw error('CONTINUOUS_OPERATION_PENDING');
            return work();
        },
    );
}

/** 读取可等待原提交释放锁，但等待和网络合计有上限；取消的旧读取不能清理新页面保留的编号。 */
async function coordinatedRead<T>(
    owner: number,
    signal: AbortSignal | undefined,
    work: (signal: AbortSignal) => Promise<T>,
): Promise<T> {
    if (typeof navigator === 'undefined' || !navigator.locks?.request)
        throw error('CONTINUOUS_OPERATION_COORDINATION_UNAVAILABLE');
    const controller = new AbortController();
    let timedOut = false;
    const cancel = () => controller.abort();
    if (signal?.aborted) cancel();
    signal?.addEventListener('abort', cancel, {once: true});
    const timer = setTimeout(() => {
        timedOut = true;
        cancel();
    }, 15_000);
    try {
        return await navigator.locks.request(
            key(owner),
            {mode: 'exclusive', signal: controller.signal},
            () => work(controller.signal),
        );
    } catch (failure) {
        if (timedOut && !signal?.aborted) throw error('CONTINUOUS_OPERATION_READ_TIMEOUT');
        throw failure;
    } finally {
        clearTimeout(timer);
        signal?.removeEventListener('abort', cancel);
    }
}

/** 核对原主体、原输入和完整结果；原命令快照不能被当作当前运行状态直接写入缓存。 */
export function confirmContinuousOperation(
    pending: PendingContinuousOperation,
    value: unknown,
): ConfirmedContinuousOperation {
    try {
        if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
        const row = value as Record<string, unknown>;
        const request = pending.request;
        if (
            row.operationId !== request.operationId ||
            row.type !== request.type ||
            row.status !== 'COMPLETED' ||
            typeof row.paperRunId !== 'string' ||
            !identity.test(row.paperRunId) ||
            typeof row.completedAt !== 'string' ||
            !Number.isFinite(Date.parse(row.completedAt))
        )
            throw new Error();
        if (request.type === 'CREATE') {
            if (
                row.publishId !== request.publishId ||
                continuousBudget(row.budget) !== continuousBudget(request.budget)
            )
                throw new Error();
        } else if (
            row.publishId !== null ||
            row.budget !== null ||
            row.paperRunId !== request.paperRunId
        )
            throw new Error();
        const result = parseContinuousSimStatus(row.result, row.paperRunId);
        if (!result || result.status !== (request.type === 'PAUSE' ? 'STOPPED' : 'RUNNING'))
            throw new Error();
        return {
            ownerUserId: pending.ownerUserId,
            operationId: request.operationId,
            type: request.type,
            publishId: request.type === 'CREATE' ? request.publishId : null,
            budget: request.type === 'CREATE' ? continuousBudget(row.budget) : null,
            paperRunId: row.paperRunId,
            status: 'COMPLETED',
            result,
            completedAt: row.completedAt,
        };
    } catch {
        throw error('CONTINUOUS_OPERATION_RESPONSE_INVALID');
    }
}

function rejectedInitially(failure: AppApiError, restoring: boolean): boolean {
    if (restoring) return false;
    if ([400, 403, 404, 422].includes(failure.status)) return true;
    // 只识别明确的事务前置拒绝；忙、身份冲突及未识别409继续未知，不能释放原编号。
    return (
        failure.status === 409 &&
        failure.code === 'STATE_CONFLICT' &&
        [
            'CONTINUOUS_SIM_DISABLED',
            'CONTINUOUS_SIM_ALREADY_STOPPED',
            'CONTINUOUS_SIM_NOT_STOPPED',
            'CONTINUOUS_SIM_SEED_INVALID',
            'CONTINUOUS_SIM_CAPACITY_EXCEEDED',
            'DATA_REVISION_DETECTED',
            'STRATEGY_INPUT_REJECTED',
            'SIM_EXECUTION_BLOCKED',
        ].includes(failure.message)
    );
}

async function execute(pending: PendingContinuousOperation, original: Session, restoring: boolean) {
    requireSession(original, true);
    requireOriginal(pending);
    inFlight.add(pending.request.operationId);
    try {
        retain({...pending, phase: 'SUBMITTING'});
        requireSession(original, true);
        requireOriginal(pending);
        const {data} = await apiClient.post<unknown>(
            '/paper-trading/strategy-sim/continuous-operations',
            pending.request,
        );
        const result = confirmContinuousOperation(pending, data);
        requireSession(original, true);
        clear(pending);
        return result;
    } catch (failure) {
        const problem = failure as AppApiError;
        const current = useAuthStore.getState();
        if (
            current.currentUser?.userId === original.owner &&
            selectSessionIdentity(current) === original.token &&
            canOperateContinuous() &&
            rejectedInitially(problem, restoring)
        )
            clear(pending);
        else {
            try {
                retain({...pending, phase: 'UNKNOWN'});
            } catch {
                /* 最初保存的编号继续保留；不能用后续存储故障另起命令。 */
            }
        }
        throw failure;
    } finally {
        inFlight.delete(pending.request.operationId);
        notify();
    }
}

export const continuousOperations = {
    async submit(input: ContinuousOperationInput) {
        const original = session(true);
        return coordinated(original.owner, async () => {
            requireSession(original, true);
            if (readContinuousOperation(original.owner).kind !== 'none')
                throw error('CONTINUOUS_OPERATION_PENDING');
            if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function')
                throw error('CONTINUOUS_OPERATION_IDENTITY_UNAVAILABLE');
            const request = {...input, operationId: crypto.randomUUID()};
            if (!validContinuousOperationRequest(request))
                throw error('CONTINUOUS_OPERATION_INPUT_INVALID');
            missingResult = null;
            const pending: PendingContinuousOperation = {
                ownerUserId: original.owner,
                request,
                phase: 'SUBMITTING',
                recordedAt: Date.now(),
            };
            retain(pending, true);
            return execute(pending, original, false);
        });
    },
    async lookup(pending: PendingContinuousOperation, signal?: AbortSignal) {
        const original = session(false, pending);
        requireOriginal(pending);
        return coordinatedRead(original.owner, signal, async (readSignal) => {
            requireSession(original, false);
            requireOriginal(pending);
            missingResult = null;
            try {
                const {data} = await apiClient.get<unknown>(
                    `/paper-trading/strategy-sim/continuous-operations/${pending.request.operationId}`,
                    {signal: readSignal},
                );
                if (readSignal.aborted) throw error('CONTINUOUS_OPERATION_READ_CANCELLED');
                const result = confirmContinuousOperation(pending, data);
                requireSession(original, false);
                clear(pending);
                return result;
            } catch (failure) {
                if ((failure as AppApiError).status === 404) {
                    requireSession(original, false);
                    requireOriginal(pending);
                    missingResult = {
                        owner: original.owner,
                        input: inputKey(pending.request),
                        token: original.token,
                    };
                }
                throw failure;
            }
        });
    },
    async resume(pending: PendingContinuousOperation) {
        const original = session(true, pending);
        requireOriginal(pending);
        return coordinated(original.owner, async () => {
            requireSession(original, true);
            requireOriginal(pending);
            if (
                !missingResult ||
                missingResult.owner !== original.owner ||
                missingResult.token !== original.token ||
                missingResult.input !== inputKey(pending.request)
            )
                throw error('CONTINUOUS_OPERATION_LOOKUP_REQUIRED');
            missingResult = null;
            return execute(pending, original, true);
        });
    },
};

export function continuousOperationErrorKey(code: string): string | null {
    const messages: Record<string, string> = {
        CONTINUOUS_OPERATION_STORAGE_UNAVAILABLE: 'continuousOperations.storageUnavailable',
        CONTINUOUS_OPERATION_COORDINATION_UNAVAILABLE:
            'continuousOperations.coordinationUnavailable',
        CONTINUOUS_OPERATION_PENDING: 'continuousOperations.pendingMeaning',
        CONTINUOUS_OPERATION_PERMISSION: 'continuousOperations.permission',
        CONTINUOUS_OPERATION_SCOPE_CHANGED: 'continuousOperations.scopeChanged',
        CONTINUOUS_OPERATION_IDENTITY_UNAVAILABLE: 'continuousOperations.identityUnavailable',
        CONTINUOUS_OPERATION_INPUT_INVALID: 'continuousOperations.invalidInput',
        CONTINUOUS_OPERATION_RESPONSE_INVALID: 'continuousOperations.invalidResponse',
        CONTINUOUS_OPERATION_LOOKUP_REQUIRED: 'continuousOperations.lookupRequired',
        CONTINUOUS_OPERATION_READ_TIMEOUT: 'continuousOperations.readTimeout',
    };
    return messages[code] ?? null;
}
