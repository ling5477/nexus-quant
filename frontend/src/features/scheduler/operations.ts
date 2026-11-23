import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {ScheduledJobControl} from './types';

export interface ScheduledOperationRequest {
    operationId: string;
    type: 'UPDATE_CONTROL' | 'RUN_ONCE';
    jobKey: string;
    enabled?: boolean;
    fixedDelayMs?: number;
    expectedVersion?: number;
}
export interface ScheduledOperationResponse {
    operationId: string;
    type: ScheduledOperationRequest['type'];
    jobKey: string;
    status: 'STARTING' | 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'OUTCOME_UNKNOWN';
    createdAt: string;
    startedAt: string | null;
    completedAt: string | null;
    result: 'SUCCESS' | 'FAILED' | 'SKIPPED' | null;
    reasonCode: string | null;
    control: ScheduledJobControl | null;
}
export interface PendingScheduledOperation {
    ownerUserId: number;
    request: ScheduledOperationRequest;
    phase: 'SUBMITTING' | 'UNCONFIRMED';
}

export const scheduledOperationChanged = 'nq:scheduled-operation-changed';
const inFlight = new Set<string>();
const key = (owner: number) => `nq:scheduled-operation:${owner}`;
const notify = () => window.dispatchEvent(new Event(scheduledOperationChanged));
const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i;

function error(code: string): AppApiError {
    return Object.assign(new Error(code), {
        name: 'AppApiError',
        status: 0,
        code,
        method: 'POST',
        fieldErrors: [],
    });
}

/** 只要原记录存在或无法读取，就禁止生成新请求编号；不以存储错误伪装为无待确认操作。 */
export function hasScheduledOperation(owner?: number): boolean {
    if (!owner || typeof window === 'undefined') return false;
    try {
        return sessionStorage.getItem(key(owner)) !== null;
    } catch {
        return true;
    }
}

export function validScheduledOperationRequest(value: ScheduledOperationRequest): boolean {
    if (!value || !uuid.test(value.operationId) || !/^[A-Z][A-Z0-9_]{1,47}$/.test(value.jobKey))
        return false;
    if (
        Object.keys(value).some(
            (field) =>
                ![
                    'operationId',
                    'type',
                    'jobKey',
                    'enabled',
                    'fixedDelayMs',
                    'expectedVersion',
                ].includes(field),
        )
    )
        return false;
    if (value.type === 'RUN_ONCE')
        return (
            value.enabled === undefined &&
            value.fixedDelayMs === undefined &&
            value.expectedVersion === undefined
        );
    return (
        value.type === 'UPDATE_CONTROL' &&
        Number.isSafeInteger(value.expectedVersion) &&
        value.expectedVersion! >= 0 &&
        (value.enabled !== undefined || value.fixedDelayMs !== undefined) &&
        (value.enabled === undefined || typeof value.enabled === 'boolean') &&
        (value.fixedDelayMs === undefined ||
            (Number.isSafeInteger(value.fixedDelayMs) &&
                value.fixedDelayMs >= 1000 &&
                value.fixedDelayMs <= 86400000))
    );
}

/** 仅保存当前用户的非敏感调度输入；刷新后失去原网络观察时转待查询，绝不自动重发。 */
export function readScheduledOperation(owner?: number): PendingScheduledOperation | null {
    if (!owner || typeof window === 'undefined') return null;
    try {
        const raw = sessionStorage.getItem(key(owner));
        if (!raw || raw.length > 4096) return null;
        const value = JSON.parse(raw) as PendingScheduledOperation;
        if (
            value.ownerUserId !== owner ||
            !['SUBMITTING', 'UNCONFIRMED'].includes(value.phase) ||
            !validScheduledOperationRequest(value.request)
        )
            return null;
        return value.phase === 'SUBMITTING' && !inFlight.has(value.request.operationId)
            ? {...value, phase: 'UNCONFIRMED'}
            : value;
    } catch {
        return null;
    }
}

function retain(value: PendingScheduledOperation) {
    try {
        sessionStorage.setItem(key(value.ownerUserId), JSON.stringify(value));
    } catch {
        throw error('SCHEDULER_OPERATION_STORAGE_UNAVAILABLE');
    }
    notify();
}

function clear(value: PendingScheduledOperation) {
    if (
        readScheduledOperation(value.ownerUserId)?.request.operationId === value.request.operationId
    )
        sessionStorage.removeItem(key(value.ownerUserId));
    notify();
}

/** 收到的回执必须属于原请求，且终态具有对应结果；不以新的当前任务状态覆盖原操作快照。 */
export function validScheduledOperationResult(
    request: ScheduledOperationRequest,
    value: ScheduledOperationResponse,
): boolean {
    if (
        !value ||
        value.operationId !== request.operationId ||
        value.type !== request.type ||
        value.jobKey !== request.jobKey ||
        !['STARTING', 'RUNNING', 'COMPLETED', 'REJECTED', 'OUTCOME_UNKNOWN'].includes(value.status)
    )
        return false;
    if (value.status === 'COMPLETED') {
        if (!value.completedAt) return false;
        if (request.type === 'RUN_ONCE')
            return ['SUCCESS', 'FAILED', 'SKIPPED'].includes(value.result ?? '');
        const result = value.control;
        return (
            result?.jobKey === request.jobKey &&
            Number.isSafeInteger(result.version) &&
            (result.version === request.expectedVersion ||
                result.version === request.expectedVersion! + 1) &&
            (request.enabled === undefined || result.enabled === request.enabled) &&
            (request.fixedDelayMs === undefined || result.fixedDelayMs === request.fixedDelayMs)
        );
    }
    return value.status !== 'REJECTED' || Boolean(value.completedAt && value.reasonCode);
}

export function scheduledOperationRejected(
    value: Partial<AppApiError>,
    restoring = false,
): boolean {
    return (
        ([400, 404, 409, 422].includes(value.status ?? 0) ||
            (!restoring && [401, 403].includes(value.status ?? 0))) &&
        value.code !== 'SCHEDULER_OPERATION_ID_CONFLICT'
    );
}

function accept(pending: PendingScheduledOperation, value: ScheduledOperationResponse) {
    if (useAuthStore.getState().currentUser?.userId !== pending.ownerUserId)
        throw error('SCHEDULER_OPERATION_SCOPE_CHANGED');
    if (!validScheduledOperationResult(pending.request, value))
        throw error('SCHEDULER_OPERATION_RESPONSE_INVALID');
    if (value.status === 'COMPLETED' || value.status === 'REJECTED') clear(pending);
    else retain({...pending, phase: 'UNCONFIRMED'});
    return value;
}

async function execute(pending: PendingScheduledOperation, restoring = false) {
    inFlight.add(pending.request.operationId);
    try {
        retain({...pending, phase: 'SUBMITTING'});
        const {data} = await apiClient.post<ScheduledOperationResponse>(
            '/scheduler/operations',
            pending.request,
        );
        return accept(pending, data);
    } catch (failure) {
        const value = failure as AppApiError;
        if (scheduledOperationRejected(value, restoring)) clear(pending);
        else if (value.code !== 'SCHEDULER_OPERATION_STORAGE_UNAVAILABLE') {
            try {
                retain({...pending, phase: 'UNCONFIRMED'});
            } catch {
                /* 首次提交前保存的原记录仍保留，不能用第二次存储错误覆盖原失败。 */
            }
        }
        throw failure;
    } finally {
        inFlight.delete(pending.request.operationId);
        notify();
    }
}

/** 发送前保存身份；查询仅GET，继续原请求须由用户确认，并匹配当前用户及会话中的完整输入。 */
export const scheduledOperations = {
    async submit(input: Omit<ScheduledOperationRequest, 'operationId'>) {
        const owner = useAuthStore.getState().currentUser?.userId;
        if (!owner || !Number.isSafeInteger(owner) || owner <= 0) throw error('UNAUTHORIZED');
        if (hasScheduledOperation(owner)) throw error('SCHEDULER_OPERATION_PENDING');
        const pending: PendingScheduledOperation = {
            ownerUserId: owner,
            request: {...input, operationId: crypto.randomUUID()},
            phase: 'SUBMITTING',
        };
        if (!validScheduledOperationRequest(pending.request))
            throw error('INVALID_SCHEDULER_OPERATION');
        return execute(pending);
    },
    async lookup(pending: PendingScheduledOperation) {
        const {data} = await apiClient.get<ScheduledOperationResponse>(
            `/scheduler/operations/${pending.request.operationId}`,
        );
        return accept(pending, data);
    },
    async resume(pending: PendingScheduledOperation) {
        const stored = readScheduledOperation(pending.ownerUserId);
        if (
            useAuthStore.getState().currentUser?.userId !== pending.ownerUserId ||
            JSON.stringify(stored?.request) !== JSON.stringify(pending.request)
        )
            throw error('SCHEDULER_OPERATION_SCOPE_CHANGED');
        return execute(pending, true);
    },
};
