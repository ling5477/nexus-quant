import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {
    ExchangeAccountActiveCredentialResponse,
    ExchangeCredentialType,
} from '../types/accounts';

/** 保留三类费用的流动性/买卖方向分量；优惠只表示条件，不能推断实际费用资产或余额足额。 */
export interface SpotCommissionTerms {
    nativeSymbol: string;
    standard: CommissionRates;
    tax: CommissionRates;
    special: CommissionRates;
    discount: {
        enabledForAccount: boolean;
        enabledForSymbol: boolean;
        asset: string;
        multiplier: string;
    };
    responseSha256: string;
}
export interface CommissionRates {
    maker: string;
    taker: string;
    buyer: string;
    seller: string;
}

/** 当前观察的费率证据；与历史真实费率及已经发生的成交费用分开，精确值不得经过浮点。 */
export interface AccountFeeObservation {
    observationId: string;
    operationId: string;
    exchangeAccountId: number;
    credentialId: number;
    exchangeCode: string;
    tradeEnv: string;
    symbol: string;
    source: string;
    rateConvention: string;
    makerProviderRate: string | null;
    takerProviderRate: string | null;
    makerCostRate: string | null;
    takerCostRate: string | null;
    tier: string | null;
    providerAt: string | null;
    observedAt: string;
    expiresAt: string;
    sourceSha256: string;
    providerTimeBasis?: 'PROVIDER_FEE_TIMESTAMP' | 'HTTP_RESPONSE_DATE' | 'NOT_PROVIDED';
    commissionTerms?: SpotCommissionTerms | null;
    status: 'SUCCEEDED';
    scope: 'CURRENT_ACCOUNT_AT_OBSERVATION';
}
/** 来源可同步与已有观察历史分别表达；来源关闭仍允许查询旧证据。 */
export interface AccountFeeHistory {
    items: AccountFeeObservation[];
    hasMore: boolean;
    nextBefore: string | null;
    synchronizationAvailable: boolean;
    generatedAt: string;
    scope: string;
}
/** 会话只保留非敏感操作身份；不保存费率事实、认证材料或供应方响应。 */
export interface PendingFeeSync {
    ownerUserId: number;
    accountId: number;
    credentialId: number;
    symbol: string;
    operationId: string;
    phase: 'SUBMITTING' | 'UNKNOWN';
}
const scalarFields = [
    'makerProviderRate',
    'takerProviderRate',
    'makerCostRate',
    'takerCostRate',
] as const;
const exactFields = new Set([...scalarFields, 'maker', 'taker', 'buyer', 'seller', 'multiplier']);
const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i;
const symbolPattern = /^[A-Z0-9]{1,20}-[A-Z0-9]{1,20}$/;
const storageKey = (owner: number) => `nq:account-fee-sync:${owner}`;
const inFlight = new Set<string>();
export const feeSyncChanged = 'nq:account-fee-sync-changed';
const notify = () => window.dispatchEvent(new Event(feeSyncChanged));
function failure(code: string): AppApiError {
    return Object.assign(new Error(code), {
        name: 'AppApiError',
        status: 0,
        code,
        method: 'POST',
        fieldErrors: [],
    });
}

/** 存储不可读也阻止新身份；不能把浏览器故障解释为原请求未发生。 */
export function hasFeeSyncRecord(owner?: number): boolean {
    if (!owner || typeof window === 'undefined') return false;
    try {
        return sessionStorage.getItem(storageKey(owner)) !== null;
    } catch {
        return true;
    }
}
/** 重载后把在途身份转为未知，原请求不会被自动重发；损坏或夹带材料的记录不被执行。 */
export function readPendingFeeSync(owner?: number): PendingFeeSync | null {
    if (!owner || typeof window === 'undefined') return null;
    try {
        const raw = sessionStorage.getItem(storageKey(owner));
        if (!raw || raw.length > 1024) return null;
        const value = JSON.parse(raw) as PendingFeeSync;
        if (
            Object.keys(value).some(
                (key) =>
                    ![
                        'ownerUserId',
                        'accountId',
                        'credentialId',
                        'symbol',
                        'operationId',
                        'phase',
                    ].includes(key),
            ) ||
            value.ownerUserId !== owner ||
            !Number.isSafeInteger(value.accountId) ||
            value.accountId <= 0 ||
            !Number.isSafeInteger(value.credentialId) ||
            value.credentialId <= 0 ||
            typeof value.symbol !== 'string' ||
            !symbolPattern.test(value.symbol) ||
            typeof value.operationId !== 'string' ||
            !uuid.test(value.operationId) ||
            !['SUBMITTING', 'UNKNOWN'].includes(value.phase)
        )
            return null;
        return value.phase === 'SUBMITTING' && !inFlight.has(value.operationId)
            ? {...value, phase: 'UNKNOWN'}
            : value;
    } catch {
        return null;
    }
}
/** 提交前先持久化非敏感原身份；保存失败禁止发请求，未知结果只更新该身份的阶段。 */
function retain(value: PendingFeeSync) {
    try {
        sessionStorage.setItem(storageKey(value.ownerUserId), JSON.stringify(value));
    } catch {
        throw failure('FEE_SYNC_STORAGE_UNAVAILABLE');
    }
    notify();
}
/** 请求前与响应后都核对当前用户，防止异步结果跨会话清理身份或确认另一用户的操作。 */
function requireOwner(value: PendingFeeSync) {
    if (useAuthStore.getState().currentUser?.userId !== value.ownerUserId)
        throw failure('FEE_SYNC_SCOPE_CHANGED');
}
/** 只有当前用户且UUID仍匹配时才清理，旧响应不能删除后续操作的身份。 */
function clear(value: PendingFeeSync) {
    requireOwner(value);
    if (readPendingFeeSync(value.ownerUserId)?.operationId === value.operationId)
        sessionStorage.removeItem(storageKey(value.ownerUserId));
    notify();
}
/** 成功必须匹配原账户、凭据、标的和身份，且保留来源及精确费率；HTTP成功本身不足以清除未知状态。 */
export function validateFeeReceipt(pending: PendingFeeSync, receipt: AccountFeeObservation) {
    if (
        !receipt ||
        receipt.operationId !== pending.operationId ||
        receipt.exchangeAccountId !== pending.accountId ||
        receipt.credentialId !== pending.credentialId ||
        receipt.symbol !== pending.symbol ||
        receipt.status !== 'SUCCEEDED' ||
        receipt.scope !== 'CURRENT_ACCOUNT_AT_OBSERVATION' ||
        !validFeeSource(receipt) ||
        !uuid.test(receipt.observationId) ||
        !/^[a-f0-9]{64}$/.test(receipt.sourceSha256) ||
        [receipt.observedAt, receipt.expiresAt].some(
            (time) => typeof time !== 'string' || !Number.isFinite(Date.parse(time)),
        )
    ) {
        throw failure('FEE_SYNC_RESPONSE_INVALID');
    }
}

/** 来源按各自正式符号和schema检查；Binance必须有完整方向条件，不能接受OKX旧字段拼装的成功。 */
function validFeeSource(row: AccountFeeObservation): boolean {
    if (row.source === 'OKX_PRIVATE_SPOT_FEE_V5')
        return (
            row.exchangeCode === 'OKX' &&
            row.rateConvention === 'OKX_NEGATIVE_COST_V1' &&
            !row.commissionTerms &&
            (row.providerTimeBasis === undefined ||
                row.providerTimeBasis === 'PROVIDER_FEE_TIMESTAMP') &&
            typeof row.providerAt === 'string' &&
            Number.isFinite(Date.parse(row.providerAt)) &&
            scalarFields.every(
                (key) =>
                    typeof row[key] === 'string' &&
                    /^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(row[key] as string),
            )
        );
    if (
        row.source !== 'BINANCE_PRIVATE_SPOT_COMMISSION' ||
        row.exchangeCode !== 'BINANCE' ||
        row.rateConvention !== 'BINANCE_POSITIVE_COST_V1' ||
        scalarFields.some((key) => row[key] !== null) ||
        row.tier !== null ||
        !validCommissionTerms(row.commissionTerms) ||
        row.commissionTerms.nativeSymbol !== row.symbol.replace('-', '')
    )
        return false;
    return row.providerTimeBasis === 'NOT_PROVIDED'
        ? row.providerAt === null
        : row.providerTimeBasis === 'HTTP_RESPONSE_DATE' &&
              typeof row.providerAt === 'string' &&
              Number.isFinite(Date.parse(row.providerAt));
}

/** 有界闭合schema；费用字符串保留12位原生精度，零与缺失分别处理，不用浮点校正原值。 */
export function validCommissionTerms(value: unknown): value is SpotCommissionTerms {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
    const terms = value as SpotCommissionTerms;
    const keys = Object.keys(terms);
    if (
        keys.length !== 6 ||
        !['nativeSymbol', 'standard', 'tax', 'special', 'discount', 'responseSha256'].every((key) =>
            keys.includes(key),
        ) ||
        typeof terms.nativeSymbol !== 'string' ||
        !/^[A-Z0-9]{2,40}$/.test(terms.nativeSymbol) ||
        typeof terms.responseSha256 !== 'string' ||
        !/^[a-f0-9]{64}$/.test(terms.responseSha256)
    )
        return false;
    const decimal = (rate: unknown) =>
        typeof rate === 'string' && /^-?(?:0(?:\.\d{1,12})?|1(?:\.0{1,12})?)$/.test(rate);
    for (const group of [terms.standard, terms.tax, terms.special]) {
        if (
            !group ||
            typeof group !== 'object' ||
            Object.keys(group).length !== 4 ||
            !['maker', 'taker', 'buyer', 'seller'].every((key) =>
                decimal(group[key as keyof CommissionRates]),
            )
        )
            return false;
    }
    const offer = terms.discount;
    return Boolean(
        offer &&
            typeof offer === 'object' &&
            Object.keys(offer).length === 4 &&
            typeof offer.enabledForAccount === 'boolean' &&
            typeof offer.enabledForSymbol === 'boolean' &&
            typeof offer.asset === 'string' &&
            /^[A-Z0-9]{1,20}$/.test(offer.asset) &&
            decimal(offer.multiplier) &&
            !offer.multiplier.startsWith('-'),
    );
}

/** 只有首次提交的明确字段/权限拒绝可清身份；通用409及恢复拒绝保留原身份，以免丢失此前成功结果。 */
export function feeSyncRejected(error: Partial<AppApiError>, recovery = false): boolean {
    return !recovery && [400, 401, 403, 404, 422].includes(error.status ?? 0);
}
/** 首次及显式恢复都保留原目标和UUID；恢复被拒绝仍保持未知，不能推断此前请求没有提交。 */
async function execute(pending: PendingFeeSync, recovery = false): Promise<AccountFeeObservation> {
    requireOwner(pending);
    inFlight.add(pending.operationId);
    try {
        retain({...pending, phase: 'SUBMITTING'});
        const {data} = await apiClient.post<AccountFeeObservation>(
            `/exchange-accounts/${pending.accountId}/fees/operations/${pending.operationId}`,
            {credentialId: pending.credentialId, symbol: pending.symbol},
            {transformResponse: [(raw: string) => parseExactJson(raw, exactFields)]},
        );
        requireOwner(pending);
        validateFeeReceipt(pending, data);
        clear(pending);
        return data;
    } catch (error) {
        if (feeSyncRejected(error as AppApiError, recovery)) clear(pending);
        else if ((error as AppApiError).code !== 'FEE_SYNC_STORAGE_UNAVAILABLE') {
            try {
                retain({...pending, phase: 'UNKNOWN'});
            } catch {
                /* 原身份已在提交前保存，保留最初失败原因。 */
            }
        }
        throw error;
    } finally {
        inFlight.delete(pending.operationId);
        notify();
    }
}

/** 历史和结果是只读；同步须显式触发，重放始终使用原身份和目标，不自动刷新过期观察。 */
export const accountFeesApi = {
    async history(
        accountId: number,
        symbol: string,
        before?: string,
        signal?: AbortSignal,
    ): Promise<AccountFeeHistory> {
        return (
            await apiClient.get<AccountFeeHistory>(`/exchange-accounts/${accountId}/fees`, {
                params: {symbol, before, limit: 20},
                signal,
                transformResponse: [(raw: string) => parseExactJson(raw, exactFields)],
            })
        ).data;
    },
    async activeCredential(
        accountId: number,
        signal?: AbortSignal,
        credentialType: ExchangeCredentialType = 'OKX_API_V5',
    ): Promise<ExchangeAccountActiveCredentialResponse> {
        return (
            await apiClient.get<ExchangeAccountActiveCredentialResponse>(
                `/exchange-accounts/${accountId}/credentials/active`,
                {
                    params: {credentialType},
                    signal,
                },
            )
        ).data;
    },
    async submit(accountId: number, credentialId: number, symbol: string) {
        const owner = useAuthStore.getState().currentUser?.userId;
        if (!owner) throw failure('UNAUTHORIZED');
        if (
            !Number.isSafeInteger(accountId) ||
            accountId <= 0 ||
            !Number.isSafeInteger(credentialId) ||
            credentialId <= 0 ||
            !symbolPattern.test(symbol)
        ) {
            throw failure('FEE_SYNC_TARGET_INVALID');
        }
        if (hasFeeSyncRecord(owner)) throw failure('FEE_SYNC_PENDING');
        return execute({
            ownerUserId: owner,
            accountId,
            credentialId,
            symbol,
            operationId: crypto.randomUUID(),
            phase: 'SUBMITTING',
        });
    },
    async result(pending: PendingFeeSync, signal?: AbortSignal) {
        requireOwner(pending);
        const {data} = await apiClient.get<AccountFeeObservation>(
            `/exchange-accounts/${pending.accountId}/fees/operations/${pending.operationId}`,
            {
                signal,
                transformResponse: [(raw: string) => parseExactJson(raw, exactFields)],
            },
        );
        requireOwner(pending);
        validateFeeReceipt(pending, data);
        return data;
    },
    async recover(pending: PendingFeeSync) {
        if (readPendingFeeSync(pending.ownerUserId)?.operationId !== pending.operationId)
            throw failure('FEE_SYNC_SCOPE_CHANGED');
        return execute(pending, true);
    },
    confirm: clear,
};
