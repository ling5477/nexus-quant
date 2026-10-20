import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {ExchangeAccountSummary} from '../types/accounts';

export interface AccountCommandRequest {
    type: 'CREATE' | 'UPDATE' | 'ENABLE' | 'DISABLE' | 'SET_DEFAULT' | 'RESOLVE_SIM';
    accountId?: number;
    exchangeCode?: string;
    tradeEnv?: string;
    accountAlias?: string;
    externalAccountRef?: string | null;
}
export interface PendingAccountCommand {
    ownerUserId: number;
    commandId: string;
    request: AccountCommandRequest;
    phase: 'SUBMITTING' | 'UNKNOWN';
}
export interface AccountCommandResponse {
    commandId: string;
    type: AccountCommandRequest['type'];
    status: 'SUCCEEDED';
    completedAt: string;
    result: ExchangeAccountSummary;
}

export const accountCommandChanged = 'nq:account-command-changed';
const inFlight = new Set<string>();
const storageKey = (owner: number) => `nq:account-command:${owner}`;
const notify = () => window.dispatchEvent(new Event(accountCommandChanged));

/** 只要会话中有原命令记录就阻止新提交；无法读取存储时按仍有记录处理，避免重复操作。 */
export function hasAccountCommandRecord(owner?: number): boolean {
    if (!owner || typeof window === 'undefined') return false;
    try {
        return sessionStorage.getItem(storageKey(owner)) !== null;
    } catch {
        return true;
    }
}

/** 只保留当前用户的非凭据元数据请求身份；它不是账户事实或操作结果。 */
export function readPendingAccountCommand(owner?: number): PendingAccountCommand | null {
    if (!owner || owner <= 0 || typeof window === 'undefined') return null;
    try {
        const raw = sessionStorage.getItem(storageKey(owner));
        if (!raw || raw.length > 8192) return null;
        const value = JSON.parse(raw) as PendingAccountCommand;
        if (
            value.ownerUserId !== owner ||
            !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(value.commandId) ||
            !['SUBMITTING', 'UNKNOWN'].includes(value.phase) ||
            !value.request ||
            !['CREATE', 'UPDATE', 'ENABLE', 'DISABLE', 'SET_DEFAULT', 'RESOLVE_SIM'].includes(
                value.request.type,
            )
        )
            return null;
        const allowed = [
            'type',
            'accountId',
            'exchangeCode',
            'tradeEnv',
            'accountAlias',
            'externalAccountRef',
        ];
        if (Object.keys(value.request).some((key) => !allowed.includes(key))) return null;
        if (
            value.request.accountId !== undefined &&
            (!Number.isSafeInteger(value.request.accountId) || value.request.accountId <= 0)
        )
            return null;
        for (const key of [
            'exchangeCode',
            'tradeEnv',
            'accountAlias',
            'externalAccountRef',
        ] as const) {
            const text = value.request[key];
            if (text != null && (typeof text !== 'string' || text.length > 128)) return null;
        }
        // 重载后浏览器已无法观察原请求；保留身份并转为待查询，而非再次提交。
        return value.phase === 'SUBMITTING' && !inFlight.has(value.commandId)
            ? {...value, phase: 'UNKNOWN'}
            : value;
    } catch {
        return null;
    }
}

function clientError(code: string): AppApiError {
    return Object.assign(new Error(code), {
        name: 'AppApiError',
        status: 0,
        code,
        method: 'POST',
        fieldErrors: [],
    });
}

function retain(value: PendingAccountCommand) {
    try {
        sessionStorage.setItem(storageKey(value.ownerUserId), JSON.stringify(value));
    } catch {
        throw clientError('ACCOUNT_COMMAND_STORAGE_UNAVAILABLE');
    }
    notify();
}

function clear(owner: number, commandId: string) {
    const current = readPendingAccountCommand(owner);
    if (current?.commandId === commandId) sessionStorage.removeItem(storageKey(owner));
    notify();
}

/** 仅在有稳定拒绝响应时清除；认证变化和服务器/网络错误仍需查询原身份。 */
export function accountCommandRejected(error: Partial<AppApiError>, recovery = false): boolean {
    const status = error.status ?? 0;
    return (
        ([400, 404, 409, 422].includes(status) || (!recovery && [401, 403].includes(status))) &&
        error.code !== 'ACCOUNT_COMMAND_IDENTITY_CONFLICT'
    );
}

/** 先保存稳定身份再提交；验证回执后清除记录，网络或服务异常保留UNKNOWN，显式恢复沿用原身份。 */
async function execute(
    value: PendingAccountCommand,
    recovery = false,
): Promise<AccountCommandResponse> {
    inFlight.add(value.commandId);
    try {
        retain({...value, phase: 'SUBMITTING'});
        const {data} = await apiClient.post<AccountCommandResponse>(
            `/exchange-accounts/commands/${value.commandId}`,
            value.request,
        );
        if (
            data.commandId !== value.commandId ||
            data.type !== value.request.type ||
            data.status !== 'SUCCEEDED'
        )
            throw clientError('ACCOUNT_COMMAND_RESPONSE_INVALID');
        clear(value.ownerUserId, value.commandId);
        return data;
    } catch (failure) {
        const error = failure as AppApiError;
        if (accountCommandRejected(error, recovery)) clear(value.ownerUserId, value.commandId);
        else if (error.code !== 'ACCOUNT_COMMAND_STORAGE_UNAVAILABLE') {
            try {
                retain({...value, phase: 'UNKNOWN'});
            } catch {
                /* 原提交前保存的身份仍保留；不覆盖原失败原因。 */
            }
        }
        throw failure;
    } finally {
        inFlight.delete(value.commandId);
        notify();
    }
}

/** 新提交生成一次UUID，结果查询只读；恢复重放保留原输入并交由服务端幂等判定，不自动换身份。 */
export const accountCommandsApi = {
    async submit(request: AccountCommandRequest): Promise<ExchangeAccountSummary> {
        const owner = useAuthStore.getState().currentUser?.userId;
        if (!owner || owner <= 0) throw clientError('UNAUTHORIZED');
        if (hasAccountCommandRecord(owner)) throw clientError('ACCOUNT_COMMAND_PENDING');
        const command = {
            ownerUserId: owner,
            commandId: crypto.randomUUID(),
            request,
            phase: 'SUBMITTING' as const,
        };
        return (await execute(command)).result;
    },
    async result(commandId: string, signal?: AbortSignal): Promise<AccountCommandResponse> {
        return (
            await apiClient.get<AccountCommandResponse>(
                `/exchange-accounts/commands/${commandId}`,
                {signal},
            )
        ).data;
    },
    async recover(value: PendingAccountCommand): Promise<AccountCommandResponse> {
        if (
            useAuthStore.getState().currentUser?.userId !== value.ownerUserId ||
            readPendingAccountCommand(value.ownerUserId)?.commandId !== value.commandId
        )
            throw clientError('ACCOUNT_COMMAND_SCOPE_CHANGED');
        return execute(value, true);
    },
    confirm(value: PendingAccountCommand) {
        clear(value.ownerUserId, value.commandId);
    },
};
