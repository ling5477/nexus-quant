import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {StrategyScheduleListItem} from './types/schedules';
import {
    normalizeScheduleCreation,
    scheduleCreationFields,
    type ScheduleCreationInput,
} from './creation-input';

export interface ScheduleCreationReceipt {
    creationId: string;
    status: 'COMPLETED';
    requestVersion: 1;
    request: ScheduleCreationInput;
    result: StrategyScheduleListItem;
    traceId: string;
    completedAt: string;
}
export interface PendingScheduleCreation {
    ownerUserId: number;
    creationId: string;
    request: ScheduleCreationInput;
    phase: 'SUBMITTING' | 'UNKNOWN' | 'COMPLETED';
    recordedAt: number;
    receipt?: ScheduleCreationReceipt;
}
export type ScheduleCreationStorageState =
    | {kind: 'none'}
    | {kind: 'unavailable'}
    | {kind: 'saved'; pending: PendingScheduleCreation};

export const scheduleCreationChanged = 'nq:schedule-creation-changed';
const inFlight = new Set<string>();
const uuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const zeroUuid = '00000000-0000-0000-0000-000000000000';
let missing: string | null = null;
const key = (owner: number) => `nq:schedule-creation:v1:${owner}`;
const identity = (value: PendingScheduleCreation) => `${value.ownerUserId}:${value.creationId}`;
const error = (code: string): AppApiError =>
    Object.assign(new Error(code), {status: 0, code, fieldErrors: []});
const notify = () => {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(scheduleCreationChanged));
};
const sameRequest = (a: ScheduleCreationInput, b: ScheduleCreationInput) =>
    scheduleCreationFields.every((field) => a[field] === b[field]);

/** 先核对原输入与完整业务身份，再将回执视为成功；计划当前状态必须另读详情。 */
export function confirmScheduleCreation(
    pending: PendingScheduleCreation,
    value: ScheduleCreationReceipt,
): ScheduleCreationReceipt {
    const result = value?.result;
    const validDate = (date: unknown) =>
        typeof date === 'string' && Number.isFinite(Date.parse(date));
    if (
        !value ||
        value.creationId !== pending.creationId ||
        value.status !== 'COMPLETED' ||
        value.requestVersion !== 1 ||
        !value.request ||
        !sameRequest(pending.request, value.request) ||
        !result ||
        typeof result.scheduleJobId !== 'string' ||
        !result.scheduleJobId.startsWith('sch-') ||
        !uuid.test(result.scheduleJobId.slice(4)) ||
        !scheduleCreationFields
            .filter((field) => field !== 'windowConfig')
            .every((field) => result[field] === pending.request[field]) ||
        result.status !== (pending.request.enabled ? 'ENABLED' : 'DISABLED') ||
        typeof result.windowConfig !== 'string' ||
        typeof result.exchangeCode !== 'string' ||
        !result.exchangeCode ||
        !['SIM', 'LIVE'].includes(result.tradeEnv) ||
        !(
            result.accountId === null ||
            (typeof result.accountId === 'string' && /^[1-9]\d*$/.test(result.accountId))
        ) ||
        !validDate(result.createdAt) ||
        !validDate(result.updatedAt) ||
        !validDate(value.completedAt) ||
        typeof value.traceId !== 'string' ||
        !value.traceId
    )
        throw error('SCHEDULE_CREATION_RESPONSE_INVALID');
    return value;
}

/** 每个主体最多保留一个原请求及原回执；刷新将遗留提交转为未知，不自动发写请求。 */
export function readScheduleCreation(owner: number | undefined): ScheduleCreationStorageState {
    if (!owner || !Number.isSafeInteger(owner) || owner <= 0) return {kind: 'none'};
    try {
        const raw = localStorage.getItem(key(owner));
        if (raw === null) return {kind: 'none'};
        if (raw.length > 65536) return {kind: 'unavailable'};
        const value = JSON.parse(raw) as PendingScheduleCreation;
        if (
            !value ||
            value.ownerUserId !== owner ||
            !uuid.test(value.creationId) ||
            value.creationId === zeroUuid ||
            !value.request ||
            !sameRequest(value.request, normalizeScheduleCreation(value.request)) ||
            !['SUBMITTING', 'UNKNOWN', 'COMPLETED'].includes(value.phase) ||
            !Number.isSafeInteger(value.recordedAt) ||
            value.recordedAt <= 0
        )
            return {kind: 'unavailable'};
        if (value.phase === 'COMPLETED') {
            if (!value.receipt) return {kind: 'unavailable'};
            confirmScheduleCreation(value, value.receipt);
        }
        return {
            kind: 'saved',
            pending: {
                ...value,
                phase:
                    value.phase === 'COMPLETED'
                        ? 'COMPLETED'
                        : inFlight.has(identity(value))
                          ? 'SUBMITTING'
                          : 'UNKNOWN',
            },
        };
    } catch {
        return {kind: 'unavailable'};
    }
}

function scope(pending?: PendingScheduleCreation): number {
    const current = useAuthStore.getState().currentUser;
    if (
        !current?.authenticated ||
        !Number.isSafeInteger(current.userId) ||
        current.userId <= 0 ||
        !current.roles.some((role) =>
            ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
        )
    )
        throw error('SCHEDULE_CREATION_SCOPE_UNAVAILABLE');
    if (pending && current.userId !== pending.ownerUserId)
        throw error('SCHEDULE_CREATION_SCOPE_CHANGED');
    return current.userId;
}
function requireOriginal(pending: PendingScheduleCreation) {
    scope(pending);
    const value = readScheduleCreation(pending.ownerUserId);
    if (
        value.kind !== 'saved' ||
        value.pending.creationId !== pending.creationId ||
        !sameRequest(value.pending.request, pending.request)
    )
        throw error('SCHEDULE_CREATION_STORAGE_UNAVAILABLE');
    return value.pending;
}
function retain(pending: PendingScheduleCreation) {
    try {
        const raw = JSON.stringify(pending);
        localStorage.setItem(key(pending.ownerUserId), raw);
        if (localStorage.getItem(key(pending.ownerUserId)) !== raw)
            throw new Error('storage readback mismatch');
        notify();
    } catch {
        throw error('SCHEDULE_CREATION_STORAGE_UNAVAILABLE');
    }
}
async function coordinated<T>(owner: number, work: () => Promise<T>): Promise<T> {
    if (typeof navigator === 'undefined' || !navigator.locks?.request)
        throw error('SCHEDULE_CREATION_COORDINATION_UNAVAILABLE');
    return navigator.locks.request(
        key(owner),
        {mode: 'exclusive', ifAvailable: true},
        async (lock) => {
            if (!lock) throw error('SCHEDULE_CREATION_PENDING');
            return work();
        },
    );
}
function finish(pending: PendingScheduleCreation, data: ScheduleCreationReceipt) {
    const receipt = confirmScheduleCreation(pending, data);
    scope(pending);
    retain({...pending, phase: 'COMPLETED', receipt});
    return receipt;
}

async function execute(pending: PendingScheduleCreation, recovery: boolean) {
    requireOriginal(pending);
    inFlight.add(identity(pending));
    try {
        retain({...pending, phase: 'SUBMITTING'});
        scope(pending);
        const {data} = await apiClient.post<ScheduleCreationReceipt>(
            '/strategy-schedule-creations',
            {
                creationId: pending.creationId,
                request: pending.request,
            },
        );
        return finish(pending, data);
    } catch (failure) {
        const problem = failure as AppApiError;
        // 恢复时的拒绝不能证明原请求没执行；初次明确校验/权限拒绝才释放身份，所有409均保留。
        if (
            !recovery &&
            [400, 403, 422].includes(problem.status) &&
            useAuthStore.getState().currentUser?.userId === pending.ownerUserId
        ) {
            requireOriginal(pending);
            localStorage.removeItem(key(pending.ownerUserId));
        } else {
            try {
                retain({...pending, phase: 'UNKNOWN'});
            } catch {
                /* 提交前已保存原输入；更新标记失败也不能换号再次创建。 */
            }
        }
        throw failure;
    } finally {
        inFlight.delete(identity(pending));
        notify();
    }
}

export const scheduleCreation = {
    async submit(input: ScheduleCreationInput) {
        const owner = scope();
        const request = normalizeScheduleCreation(input);
        return coordinated(owner, async () => {
            if (readScheduleCreation(owner).kind !== 'none')
                throw error('SCHEDULE_CREATION_PENDING');
            if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function')
                throw error('SCHEDULE_CREATION_IDENTITY_UNAVAILABLE');
            if (scope() !== owner) throw error('SCHEDULE_CREATION_SCOPE_CHANGED');
            const pending: PendingScheduleCreation = {
                ownerUserId: owner,
                creationId: crypto.randomUUID(),
                request,
                phase: 'SUBMITTING',
                recordedAt: Date.now(),
            };
            missing = null;
            retain(pending);
            return execute(pending, false);
        });
    },
    async lookup(pending: PendingScheduleCreation) {
        return coordinated(pending.ownerUserId, async () => {
            requireOriginal(pending);
            missing = null;
            try {
                const {data} = await apiClient.get<ScheduleCreationReceipt>(
                    `/strategy-schedule-creations/${encodeURIComponent(pending.creationId)}`,
                );
                return finish(pending, data);
            } catch (failure) {
                if (
                    (failure as AppApiError).status === 404 &&
                    useAuthStore.getState().currentUser?.userId === pending.ownerUserId
                )
                    missing = identity(pending);
                throw failure;
            }
        });
    },
    async resume(pending: PendingScheduleCreation) {
        return coordinated(pending.ownerUserId, async () => {
            const stored = requireOriginal(pending);
            if (stored.phase === 'COMPLETED' || missing !== identity(pending))
                throw error('SCHEDULE_CREATION_LOOKUP_REQUIRED');
            missing = null;
            return execute(pending, true);
        });
    },
    async acknowledge(pending: PendingScheduleCreation) {
        return coordinated(pending.ownerUserId, async () => {
            if (requireOriginal(pending).phase !== 'COMPLETED')
                throw error('SCHEDULE_CREATION_LOOKUP_REQUIRED');
            localStorage.removeItem(key(pending.ownerUserId));
            if (localStorage.getItem(key(pending.ownerUserId)) !== null)
                throw error('SCHEDULE_CREATION_STORAGE_UNAVAILABLE');
            notify();
        });
    },
};
