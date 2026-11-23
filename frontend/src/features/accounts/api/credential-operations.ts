import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {
    ExchangeAccountCredentialSummary,
    ExchangeAccountCredentialUpsertRequest,
    ExchangeCredentialType,
} from '../types/accounts';

export type CredentialOperationType =
    | 'CREATE'
    | 'ROTATE'
    | 'VERIFY'
    | 'DISABLE'
    | 'ENABLE'
    | 'EXPIRE'
    | 'REVOKE';
export interface CredentialOperationRequest
    extends Partial<ExchangeAccountCredentialUpsertRequest> {
    type: CredentialOperationType;
    credentialType: ExchangeCredentialType;
    credentialId?: number;
    reason?: string | null;
}
export interface PendingCredentialOperation {
    ownerUserId: number;
    accountId: number;
    operationId: string;
    type: CredentialOperationType;
    credentialType: ExchangeCredentialType;
    credentialId?: number;
    phase: 'SUBMITTING' | 'UNKNOWN';
}
export interface CredentialOperationResponse {
    operationId: string;
    type: CredentialOperationType;
    status: 'SUCCEEDED';
    completedAt: string;
    result: ExchangeAccountCredentialSummary;
}
export interface CredentialHistory {
    items: ExchangeAccountCredentialSummary[];
    hasMore: boolean;
    nextBefore: number | null;
}
export const credentialOperationChanged = 'nq:credential-operation-changed';
const storageKey = (owner: number) => `nq:credential-operation:${owner}`;
const inFlight = new Set<string>();
const notify = () => window.dispatchEvent(new Event(credentialOperationChanged));
function clientError(code: string): AppApiError {
    return Object.assign(new Error(code), {
        name: 'AppApiError',
        status: 0,
        code,
        method: 'POST',
        fieldErrors: [],
    });
}
/** 原操作记录未收敛前拒绝新操作；存储不可读时保持保守阻断，不能据客户端缺失猜测失败。 */
export function hasCredentialOperationRecord(owner?: number): boolean {
    if (!owner || typeof window === 'undefined') return false;
    try {
        return sessionStorage.getItem(storageKey(owner)) !== null;
    } catch {
        return true;
    }
}
/** 只恢复非敏感身份，原材料和原因必须由用户重新输入；重载不自动重放。 */
export function readPendingCredentialOperation(owner?: number): PendingCredentialOperation | null {
    if (!owner || typeof window === 'undefined') return null;
    try {
        const raw = sessionStorage.getItem(storageKey(owner));
        if (!raw || raw.length > 2048) return null;
        const value = JSON.parse(raw) as PendingCredentialOperation;
        if (
            Object.keys(value).some(
                (key) =>
                    ![
                        'ownerUserId',
                        'accountId',
                        'operationId',
                        'type',
                        'credentialType',
                        'credentialId',
                        'phase',
                    ].includes(key),
            ) ||
            value.ownerUserId !== owner ||
            !Number.isSafeInteger(value.accountId) ||
            value.accountId <= 0 ||
            !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(value.operationId) ||
            !['CREATE', 'ROTATE', 'VERIFY', 'DISABLE', 'ENABLE', 'EXPIRE', 'REVOKE'].includes(
                value.type,
            ) ||
            !['OKX_API_V5', 'BINANCE_HMAC', 'BINANCE_ED25519'].includes(value.credentialType) ||
            !['SUBMITTING', 'UNKNOWN'].includes(value.phase) ||
            (value.type === 'CREATE'
                ? value.credentialId !== undefined
                : !Number.isSafeInteger(value.credentialId) || value.credentialId! <= 0)
        )
            return null;
        return value.phase === 'SUBMITTING' && !inFlight.has(value.operationId)
            ? {...value, phase: 'UNKNOWN'}
            : value;
    } catch {
        return null;
    }
}
function retain(value: PendingCredentialOperation) {
    try {
        sessionStorage.setItem(storageKey(value.ownerUserId), JSON.stringify(value));
    } catch {
        throw clientError('CREDENTIAL_OPERATION_STORAGE_UNAVAILABLE');
    }
    notify();
}
function clear(value: PendingCredentialOperation) {
    if (readPendingCredentialOperation(value.ownerUserId)?.operationId === value.operationId)
        sessionStorage.removeItem(storageKey(value.ownerUserId));
    notify();
}
/** 仅新操作的明确拒绝可清记录；恢复中的认证或冲突不能证明原操作失败，仍保留查询身份。 */
export function credentialOperationRejected(
    error: Partial<AppApiError>,
    recovery = false,
): boolean {
    return (
        !recovery &&
        [400, 401, 403, 404, 409, 422].includes(error.status ?? 0) &&
        error.code !== 'CREDENTIAL_OPERATION_IDENTITY_CONFLICT'
    );
}
/** 回执必须匹配原操作、账户、类型与版本链；轮换还要核对前驱，防止错误响应被当作成功。 */
export function validateCredentialReceipt(
    pending: PendingCredentialOperation,
    response: CredentialOperationResponse,
) {
    if (
        response.operationId !== pending.operationId ||
        response.type !== pending.type ||
        response.status !== 'SUCCEEDED' ||
        response.result?.exchangeAccountId !== pending.accountId ||
        response.result?.credentialType !== pending.credentialType ||
        !Number.isSafeInteger(response.result?.credentialId) ||
        response.result.credentialId <= 0 ||
        (pending.type === 'ROTATE' &&
            response.result.rotatedFromCredentialId !== pending.credentialId) ||
        (!['CREATE', 'ROTATE'].includes(pending.type) &&
            response.result.credentialId !== pending.credentialId)
    )
        throw clientError('CREDENTIAL_OPERATION_RESPONSE_INVALID');
}
/** 只持久化非敏感身份；材料仅随当前请求发送，成功响应还须核对用户未切换，未知结果不自动重发。 */
async function execute(
    pending: PendingCredentialOperation,
    request: CredentialOperationRequest,
    recovery = false,
) {
    if (useAuthStore.getState().currentUser?.userId !== pending.ownerUserId)
        throw clientError('CREDENTIAL_OPERATION_SCOPE_CHANGED');
    if (
        request.type !== pending.type ||
        request.credentialType !== pending.credentialType ||
        request.credentialId !== pending.credentialId
    )
        throw clientError('CREDENTIAL_OPERATION_SCOPE_CHANGED');
    inFlight.add(pending.operationId);
    try {
        retain({...pending, phase: 'SUBMITTING'});
        const {data} = await apiClient.post<CredentialOperationResponse>(
            `/exchange-accounts/${pending.accountId}/credentials/operations/${pending.operationId}`,
            request,
        );
        validateCredentialReceipt(pending, data);
        if (useAuthStore.getState().currentUser?.userId !== pending.ownerUserId)
            throw clientError('CREDENTIAL_OPERATION_SCOPE_CHANGED');
        clear(pending);
        return data;
    } catch (failure) {
        if (credentialOperationRejected(failure as AppApiError, recovery)) clear(pending);
        else if ((failure as AppApiError).code !== 'CREDENTIAL_OPERATION_STORAGE_UNAVAILABLE') {
            try {
                retain({...pending, phase: 'UNKNOWN'});
            } catch {
                /* 原提交前的非敏感身份仍保留。 */
            }
        }
        throw failure;
    } finally {
        inFlight.delete(pending.operationId);
        notify();
    }
}
/** 历史与原结果查询不取敏感材料；显式恢复须重新输入原材料和原因，服务端以HMAC判断同一请求。 */
export const credentialOperationsApi = {
    async history(
        accountId: number,
        before?: number,
        signal?: AbortSignal,
    ): Promise<CredentialHistory> {
        return (
            await apiClient.get<CredentialHistory>(`/exchange-accounts/${accountId}/credentials`, {
                params: {before, limit: 20},
                signal,
            })
        ).data;
    },
    async submit(accountId: number, request: CredentialOperationRequest) {
        const owner = useAuthStore.getState().currentUser?.userId;
        if (!owner) throw clientError('UNAUTHORIZED');
        if (hasCredentialOperationRecord(owner)) throw clientError('CREDENTIAL_OPERATION_PENDING');
        const pending: PendingCredentialOperation = {
            ownerUserId: owner,
            accountId,
            operationId: crypto.randomUUID(),
            type: request.type,
            credentialType: request.credentialType,
            credentialId: request.credentialId,
            phase: 'SUBMITTING',
        };
        return execute(pending, request);
    },
    async result(pending: PendingCredentialOperation, signal?: AbortSignal) {
        if (useAuthStore.getState().currentUser?.userId !== pending.ownerUserId)
            throw clientError('CREDENTIAL_OPERATION_SCOPE_CHANGED');
        const {data} = await apiClient.get<CredentialOperationResponse>(
            `/exchange-accounts/${pending.accountId}/credentials/operations/${pending.operationId}`,
            {signal},
        );
        validateCredentialReceipt(pending, data);
        if (useAuthStore.getState().currentUser?.userId !== pending.ownerUserId)
            throw clientError('CREDENTIAL_OPERATION_SCOPE_CHANGED');
        return data;
    },
    async recover(pending: PendingCredentialOperation, originalInput: CredentialOperationRequest) {
        if (
            readPendingCredentialOperation(pending.ownerUserId)?.operationId !== pending.operationId
        )
            throw clientError('CREDENTIAL_OPERATION_SCOPE_CHANGED');
        return execute(pending, originalInput, true);
    },
    confirm: clear,
};
