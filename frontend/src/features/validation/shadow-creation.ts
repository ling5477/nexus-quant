import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {
    getShadowRunCreationResult,
    materializeStrategyReleaseShadowRun,
} from './api/strategy-releases';
import type {
    ShadowRunCreationResultResponse,
    StrategyReleaseShadowRunMaterializationResponse,
} from './types/strategy-releases';

export const shadowCreationChanged = 'nq:shadow-creation-changed';
const inFlight = new Set<string>();
const publishPattern = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const commandPattern =
    /^shadow-materialization-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const runStates = new Set([
    'CREATED',
    'PRECHECKING',
    'READY',
    'RUNNING',
    'STOP_REQUESTED',
    'STOPPED',
    'COMPLETED',
    'BLOCKED',
    'FAILED',
    'CANCELLED',
]);

/** 只保存原发布、原编号和原主体；每个主体最多一项未知命令，不能保存认证材料。 */
export interface PendingShadowCreation {
    ownerUserId: number;
    publishRecordId: string;
    commandIdentity: string;
    phase: 'SUBMITTING' | 'UNKNOWN';
    recordedAt: number;
}
export type ShadowCreationStorageState =
    | {kind: 'none'}
    | {kind: 'unavailable'}
    | {kind: 'pending'; pending: PendingShadowCreation};
export interface ConfirmedShadowCreation {
    shadowRunId: string;
    publishRecordId: string;
    artifactDigest: string;
    creationStatus: 'CREATED';
    runStatus: string;
    createdAt: string;
    updatedAt: string | null;
    source: 'POST' | 'GET';
    idempotentReplay: boolean | null;
}
let missingResult: Pick<
    PendingShadowCreation,
    'ownerUserId' | 'publishRecordId' | 'commandIdentity'
> | null = null;

function error(code: string): AppApiError {
    return Object.assign(new Error(code), {status: 0, code, fieldErrors: []});
}
function key(owner: number) {
    return `nq:shadow-creation:v1:${owner}`;
}
function notify() {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(shadowCreationChanged));
}
function same(
    first: PendingShadowCreation,
    second: Pick<PendingShadowCreation, 'ownerUserId' | 'publishRecordId' | 'commandIdentity'>,
) {
    return (
        first.ownerUserId === second.ownerUserId &&
        first.publishRecordId === second.publishRecordId &&
        first.commandIdentity === second.commandIdentity
    );
}

/** 刷新后留下的 SUBMITTING 只能作为未知读取；无法读取的记录不能被当作没有待恢复命令。 */
export function readShadowCreation(owner: number | undefined): ShadowCreationStorageState {
    if (!owner || !Number.isSafeInteger(owner) || owner <= 0) return {kind: 'none'};
    try {
        const raw = localStorage.getItem(key(owner));
        if (raw === null) return {kind: 'none'};
        if (raw.length > 4096) return {kind: 'unavailable'};
        const value = JSON.parse(raw) as PendingShadowCreation;
        if (
            !value ||
            value.ownerUserId !== owner ||
            typeof value.publishRecordId !== 'string' ||
            typeof value.commandIdentity !== 'string' ||
            !publishPattern.test(value.publishRecordId) ||
            !commandPattern.test(value.commandIdentity) ||
            !['SUBMITTING', 'UNKNOWN'].includes(value.phase) ||
            !Number.isSafeInteger(value.recordedAt) ||
            value.recordedAt <= 0
        )
            return {kind: 'unavailable'};
        return {
            kind: 'pending',
            pending: {
                ...value,
                phase: inFlight.has(value.commandIdentity) ? 'SUBMITTING' : 'UNKNOWN',
            },
        };
    } catch {
        return {kind: 'unavailable'};
    }
}

function scope(pending?: PendingShadowCreation): number {
    const current = useAuthStore.getState().currentUser;
    if (
        !current ||
        !Number.isSafeInteger(current.userId) ||
        current.userId <= 0 ||
        !current.authenticated ||
        !current.roles.some((role) =>
            ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
        )
    )
        throw error('SHADOW_CREATION_SCOPE_UNAVAILABLE');
    if (pending && current.userId !== pending.ownerUserId)
        throw error('SHADOW_CREATION_SCOPE_CHANGED');
    return current.userId;
}

function requireOriginal(pending: PendingShadowCreation) {
    scope(pending);
    const stored = readShadowCreation(pending.ownerUserId);
    if (stored.kind !== 'pending' || !same(stored.pending, pending))
        throw error('SHADOW_CREATION_STORAGE_UNAVAILABLE');
}

function retain(pending: PendingShadowCreation) {
    try {
        localStorage.setItem(key(pending.ownerUserId), JSON.stringify(pending));
        const stored = readShadowCreation(pending.ownerUserId);
        if (stored.kind !== 'pending' || !same(stored.pending, pending))
            throw error('SHADOW_CREATION_STORAGE_UNAVAILABLE');
        notify();
    } catch {
        throw error('SHADOW_CREATION_STORAGE_UNAVAILABLE');
    }
}

function clearOriginal(pending: PendingShadowCreation) {
    requireOriginal(pending);
    try {
        localStorage.removeItem(key(pending.ownerUserId));
        notify();
    } catch {
        throw error('SHADOW_CREATION_STORAGE_UNAVAILABLE');
    }
}

/** 原编号写入之前持有浏览器跨标签锁，避免两个标签互相覆盖未知操作；锁不可用时不发 POST。 */
async function coordinated<T>(owner: number, work: () => Promise<T>): Promise<T> {
    if (typeof navigator === 'undefined' || !navigator.locks?.request)
        throw error('SHADOW_CREATION_COORDINATION_UNAVAILABLE');
    return navigator.locks.request(
        key(owner),
        {mode: 'exclusive', ifAvailable: true},
        async (lock) => {
            if (!lock) throw error('SHADOW_CREATION_PENDING');
            return work();
        },
    );
}

function validCommon(
    pending: PendingShadowCreation,
    value: {
        shadowRunId?: unknown;
        publishRecordId?: unknown;
        artifactDigest?: unknown;
        bindingMode?: unknown;
        createdAt?: unknown;
    },
) {
    return (
        typeof value?.shadowRunId === 'string' &&
        /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(value.shadowRunId) &&
        value.publishRecordId === pending.publishRecordId &&
        value.bindingMode === 'RELEASE_BOUND' &&
        typeof value.artifactDigest === 'string' &&
        /^[0-9a-f]{64}$/.test(value.artifactDigest) &&
        typeof value.createdAt === 'string' &&
        Number.isFinite(Date.parse(value.createdAt))
    );
}

export function confirmShadowCreationGet(
    pending: PendingShadowCreation,
    value: ShadowRunCreationResultResponse,
): ConfirmedShadowCreation {
    if (
        !validCommon(pending, value) ||
        value.creationStatus !== 'CREATED' ||
        !runStates.has(value.runStatus) ||
        typeof value.updatedAt !== 'string' ||
        !Number.isFinite(Date.parse(value.updatedAt)) ||
        Date.parse(value.updatedAt) < Date.parse(value.createdAt)
    )
        throw error('SHADOW_CREATION_RESPONSE_INVALID');
    return {
        shadowRunId: value.shadowRunId,
        publishRecordId: value.publishRecordId,
        artifactDigest: value.artifactDigest,
        creationStatus: 'CREATED',
        runStatus: value.runStatus,
        createdAt: value.createdAt,
        updatedAt: value.updatedAt,
        source: 'GET',
        idempotentReplay: null,
    };
}

/** 本地协调失败使用可理解的动作说明，保留正式接口错误的既有错误目录。 */
export function shadowCreationErrorKey(code: string): string | null {
    const messages: Record<string, string> = {
        SHADOW_CREATION_STORAGE_UNAVAILABLE: 'shadowCreation.storageUnavailable',
        SHADOW_CREATION_COORDINATION_UNAVAILABLE: 'shadowCreation.coordinationUnavailable',
        SHADOW_CREATION_PENDING: 'shadowCreation.pendingMeaning',
        SHADOW_CREATION_SCOPE_UNAVAILABLE: 'shadowCreation.permission',
        SHADOW_CREATION_SCOPE_CHANGED: 'shadowCreation.scopeChanged',
        SHADOW_CREATION_IDENTITY_UNAVAILABLE: 'shadowCreation.identityUnavailable',
        SHADOW_CREATION_LOOKUP_REQUIRED: 'shadowCreation.lookupRequired',
        SHADOW_CREATION_RESPONSE_INVALID: 'shadowCreation.invalidResponse',
        SHADOW_CREATION_RESULT_UNAVAILABLE: 'shadowCreation.queryUnavailable',
    };
    return messages[code] ?? null;
}

function confirmPost(
    pending: PendingShadowCreation,
    value: StrategyReleaseShadowRunMaterializationResponse,
): ConfirmedShadowCreation {
    if (
        !validCommon(pending, value) ||
        typeof value.idempotentReplay !== 'boolean' ||
        !runStates.has(value.status) ||
        (!value.idempotentReplay && value.status !== 'CREATED')
    )
        throw error('SHADOW_CREATION_RESPONSE_INVALID');
    return {
        shadowRunId: value.shadowRunId,
        publishRecordId: value.publishRecordId,
        artifactDigest: value.artifactDigest,
        creationStatus: 'CREATED',
        runStatus: value.status,
        createdAt: value.createdAt,
        updatedAt: null,
        source: 'POST',
        idempotentReplay: value.idempotentReplay,
    };
}

async function execute(pending: PendingShadowCreation, recovery: boolean) {
    requireOriginal(pending);
    inFlight.add(pending.commandIdentity);
    try {
        retain({...pending, phase: 'SUBMITTING'});
        const response = await materializeStrategyReleaseShadowRun(
            pending.publishRecordId,
            pending.commandIdentity,
        );
        const result = confirmPost(pending, response);
        scope(pending);
        clearOriginal(pending);
        return result;
    } catch (failure) {
        const problem = failure as AppApiError;
        // 恢复时新的准入拒绝不证明原命令失败，仍保留旧编号；初次明确前置拒绝才释放未执行身份。
        const rejected =
            !recovery &&
            ([400, 403, 404, 422].includes(problem.status ?? 0) ||
                (problem.status === 409 && problem.code === 'ADMISSION_STALE')) &&
            useAuthStore.getState().currentUser?.userId === pending.ownerUserId;
        if (rejected) clearOriginal(pending);
        else {
            try {
                retain({...pending, phase: 'UNKNOWN'});
            } catch {
                /* 最初提交前已保存的身份继续保留，不能因为更新标记失败另起命令。 */
            }
        }
        throw failure;
    } finally {
        inFlight.delete(pending.commandIdentity);
        notify();
    }
}

export const shadowCreation = {
    async submit(publishRecordId: string) {
        const owner = scope();
        if (!publishPattern.test(publishRecordId)) throw error('SHADOW_CREATION_SCOPE_UNAVAILABLE');
        return coordinated(owner, async () => {
            if (readShadowCreation(owner).kind !== 'none') throw error('SHADOW_CREATION_PENDING');
            if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function')
                throw error('SHADOW_CREATION_IDENTITY_UNAVAILABLE');
            if (scope() !== owner) throw error('SHADOW_CREATION_SCOPE_CHANGED');
            missingResult = null;
            const pending: PendingShadowCreation = {
                ownerUserId: owner,
                publishRecordId,
                commandIdentity: `shadow-materialization-${crypto.randomUUID()}`,
                phase: 'SUBMITTING',
                recordedAt: Date.now(),
            };
            // 失败关闭：浏览器未确实保存原编号时，正式创建请求绝不发出。
            retain(pending);
            return execute(pending, false);
        });
    },
    async lookup(pending: PendingShadowCreation) {
        requireOriginal(pending);
        return coordinated(pending.ownerUserId, async () => {
            requireOriginal(pending);
            missingResult = null;
            try {
                const result = confirmShadowCreationGet(
                    pending,
                    await getShadowRunCreationResult(
                        pending.publishRecordId,
                        pending.commandIdentity,
                    ),
                );
                scope(pending);
                clearOriginal(pending);
                return result;
            } catch (failure) {
                if (
                    (failure as AppApiError).status === 404 &&
                    useAuthStore.getState().currentUser?.userId === pending.ownerUserId
                )
                    missingResult = {
                        ownerUserId: pending.ownerUserId,
                        publishRecordId: pending.publishRecordId,
                        commandIdentity: pending.commandIdentity,
                    };
                throw failure;
            }
        });
    },
    async resume(pending: PendingShadowCreation) {
        requireOriginal(pending);
        return coordinated(pending.ownerUserId, async () => {
            requireOriginal(pending);
            if (!missingResult || !same(pending, missingResult))
                throw error('SHADOW_CREATION_LOOKUP_REQUIRED');
            missingResult = null;
            return execute(pending, true);
        });
    },
};
