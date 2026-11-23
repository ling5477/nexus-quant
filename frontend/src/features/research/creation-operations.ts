import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';

export type CreationKind =
    | 'STRATEGY'
    | 'VERSION'
    | 'RESEARCH'
    | 'BACKTEST'
    | 'BACKTEST_RUN'
    | 'SIM_RUN'
    | 'PAPER_RUN';
export interface PendingCreation {
    operationId: string;
    kind: CreationKind;
    parentKey: string;
    input: unknown;
}
export interface CreationReceipt {
    operationId: string;
    kind: CreationKind;
    parentKey: string;
    objectId: string;
    lookupKey: string;
}
export const creationChanged = 'nq:research-creation-changed';
const prefix = 'nq:research-creation:';
function owner() {
    const id = useAuthStore.getState().currentUser?.userId;
    if (!id || id <= 0) throw failure('RESEARCH_CREATION_STORAGE');
    return id;
}
function key(kind: CreationKind) {
    return `${prefix}${owner()}:${kind}`;
}
function failure(code: string): AppApiError {
    return Object.assign(new Error(code), {name: 'AppApiError', code, status: 0}) as AppApiError;
}
function write(kind: CreationKind, value: PendingCreation | null) {
    try {
        if (value) localStorage.setItem(key(kind), JSON.stringify(value));
        else localStorage.removeItem(key(kind));
    } catch {
        throw failure('RESEARCH_CREATION_STORAGE');
    }
    window.dispatchEvent(new Event(creationChanged));
}
/** 持久内容只保存本人的原输入与UUID；存储损坏时停止创建，不能静默换新身份。 */
export function pendingCreation(kind: CreationKind): PendingCreation | null {
    try {
        const raw = localStorage.getItem(key(kind));
        if (!raw) return null;
        const value = JSON.parse(raw) as PendingCreation;
        if (
            !/^[0-9a-f-]{36}$/i.test(value.operationId) ||
            value.kind !== kind ||
            typeof value.parentKey !== 'string' ||
            !value.input ||
            typeof value.input !== 'object'
        )
            throw new Error('invalid pending input');
        return value;
    } catch {
        throw failure('RESEARCH_CREATION_STORAGE');
    }
}
function identity(receipt: CreationReceipt, original: PendingCreation) {
    if (
        !receipt ||
        receipt.operationId !== original.operationId ||
        receipt.kind !== original.kind ||
        receipt.parentKey !== original.parentKey ||
        !receipt.objectId ||
        !receipt.lookupKey
    )
        throw failure('RESEARCH_CREATION_UNKNOWN');
    return receipt;
}
export async function queryCreation(original: PendingCreation): Promise<CreationReceipt> {
    const {data} = await apiClient.get<CreationReceipt>(
        `/research-creation-operations/${original.operationId}`,
    );
    return identity(data, original);
}
/** 重试只能消费已保存输入；不存在的回执可能仍在途，唯一键会等待同一业务事务。 */
export async function retryCreation(original: PendingCreation): Promise<CreationReceipt> {
    const requestOwner = owner();
    const saved = pendingCreation(original.kind);
    if (!saved || JSON.stringify(saved) !== JSON.stringify(original))
        throw failure('RESEARCH_CREATION_UNKNOWN');
    try {
        return await queryCreation(original);
    } catch (error) {
        if ((error as AppApiError).status !== 404) throw error;
    }
    if (!creationStillCurrent(original, requestOwner))
        throw failure('RESEARCH_CREATION_SCOPE_CHANGED');
    const {data} = await apiClient.post<CreationReceipt>(
        `/research-creation-operations/${original.operationId}`,
        {kind: original.kind, parentKey: original.parentKey, input: original.input},
    );
    return identity(data, original);
}
/** 正常成功也保留身份，直到用户已读取正式结果并确认；刷新不会丢失已创建对象。 */
export function acknowledgeCreation(original: PendingCreation) {
    const saved = pendingCreation(original.kind);
    if (saved?.operationId !== original.operationId) throw failure('RESEARCH_CREATION_UNKNOWN');
    write(original.kind, null);
}
/** 旧页、旧账户或旧异步响应不能确认并清除后来创建的UNKNOWN身份。 */
export function creationStillCurrent(original: PendingCreation, ownerId: number): boolean {
    if (useAuthStore.getState().currentUser?.userId !== ownerId) return false;
    try {
        return pendingCreation(original.kind)?.operationId === original.operationId;
    } catch {
        return false;
    }
}
export function creationResultMatches(
    original: PendingCreation,
    ownerId: number,
    result: {operationId: string; ownerId: number} | null,
): boolean {
    return Boolean(
        result &&
            result.operationId === original.operationId &&
            result.ownerId === ownerId &&
            creationStillCurrent(original, ownerId),
    );
}
export async function createRecoverably<T>(
    kind: CreationKind,
    input: unknown,
    read: (receipt: CreationReceipt) => Promise<T>,
    parentKey = '',
): Promise<T> {
    if (!navigator.locks) throw failure('RESEARCH_CREATION_STORAGE');
    const requestOwner = owner();
    const lockKey = key(kind);
    return navigator.locks.request(lockKey, async () => {
        // 等待其他标签页释放锁期间可能已切换账户，不能在旧锁下发出新主体的命令。
        if (owner() !== requestOwner) throw failure('RESEARCH_CREATION_SCOPE_CHANGED');
        let original = pendingCreation(kind);
        if (original) {
            // 新草稿不能覆盖未知原输入；已有成功身份也必须先明确确认。
            throw failure('RESEARCH_CREATION_UNKNOWN');
        }
        original = {
            operationId: crypto.randomUUID(),
            kind,
            parentKey,
            input: JSON.parse(JSON.stringify(input)),
        };
        write(kind, original);
        let submitted = false;
        try {
            const {data} = await apiClient.post<CreationReceipt>(
                `/research-creation-operations/${original.operationId}`,
                {kind, parentKey, input: original.input},
            );
            submitted = true;
            return await read(identity(data, original));
        } catch (error) {
            const status = (error as AppApiError).status;
            // 正式参数/权限拒绝没有创建事务；运输、5xx或身份冲突仍保留UNKNOWN与原请求。
            if (!submitted && [400, 403, 404, 422].includes(status)) {
                acknowledgeCreation(original);
                throw error;
            }
            throw failure('RESEARCH_CREATION_UNKNOWN');
        }
    });
}
