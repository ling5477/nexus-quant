import type {UserOperationInput} from './api';
export interface PendingUserOperation {userId: number; enabled: boolean; input: UserOperationInput;}
const prefix = 'nq:platform-user-operation:';
function key(identity: string, userId: number) {return `${prefix}${identity}:${userId}`;}

/** 回执未知时跨页面重载保留原操作；会话身份进入键，不能借另一账号重放。 */
export function readPendingUserOperation(identity: string, userId: number): PendingUserOperation | null {
    try {
        const raw = sessionStorage.getItem(key(identity, userId));
        if (!raw) return null;
        const value: unknown = JSON.parse(raw);
        if (!value || typeof value !== 'object') return null;
        const pending = value as Partial<PendingUserOperation>;
        if (pending.userId !== userId || typeof pending.enabled !== 'boolean' || !pending.input
            || typeof pending.input.operationId !== 'string' || !/^[0-9a-f-]{36}$/i.test(pending.input.operationId)
            || typeof pending.input.reason !== 'string' || !pending.input.reason.trim() || pending.input.reason.length > 500) return null;
        return pending as PendingUserOperation;
    } catch {return null;}
}
export function savePendingUserOperation(identity: string, value: PendingUserOperation) {
    try {sessionStorage.setItem(key(identity, value.userId), JSON.stringify(value));} catch {
        // 浏览器禁止存储时仍由组件保留同一操作，页面会提示结果未确认。
    }
}
export function clearPendingUserOperation(identity: string, userId: number) {
    try {sessionStorage.removeItem(key(identity, userId));} catch { /* 禁用存储时没有可清除的持久回执。 */ }
}
