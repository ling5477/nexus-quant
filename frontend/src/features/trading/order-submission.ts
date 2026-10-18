import {accountsApi} from '@/features/accounts/api/accounts';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import {tradingWorkbenchApi} from './api/trading-workbench';
import {tradingOrderStatuses} from './trading-presentation';
import type {OrderSubmitRequest, OrderView} from './types/trading-workbench';

export interface ManualOrderInput {
    symbol: string;
    side: string;
    orderType: string;
    price?: string;
    quantity: string;
}
export interface PendingOrderSubmission {
    ownerUserId: number;
    legacyAccountId: number;
    accountAlias: string;
    request: OrderSubmitRequest;
    phase: 'SUBMITTING' | 'UNKNOWN' | 'CONFIRMED';
    recordedAt: number;
    orderId?: string;
}
export type OrderSubmissionStorage =
    | {kind: 'none'}
    | {kind: 'unavailable'}
    | {kind: 'saved'; pending: PendingOrderSubmission};

export const orderSubmissionChanged = 'nq:order-submission-changed';
const inFlight = new Set<string>();
const key = (owner: number) => `nq:order-submission:v1:${owner}`;
const identity = (pending: PendingOrderSubmission) =>
    `${pending.ownerUserId}:${pending.request.clientOrderId}`;
const fields = [
    'accountId',
    'venue',
    'clientOrderId',
    'symbol',
    'side',
    'orderType',
    'price',
    'quantity',
] as const;
let missing: string | null = null;
const error = (code: string): AppApiError =>
    Object.assign(new Error(code), {status: 0, code, fieldErrors: []});
const positiveId = (id: unknown): id is number =>
    typeof id === 'number' && Number.isSafeInteger(id) && id > 0;
const notify = () => {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(orderSubmissionChanged));
};

/** 普通订单的存储为numeric(38,8)；在写请求前拒绝越界，不以浮点或静默舍入代替精确输入。 */
function amount(value: unknown): string {
    if (
        typeof value !== 'string' ||
        !/^(?:0|[1-9]\d{0,29})(?:\.\d{1,8})?$/.test(value) ||
        !/[1-9]/.test(value)
    )
        throw error('ORDER_SUBMISSION_AMOUNT_INVALID');
    return value;
}
export function normalizeManualOrder(input: ManualOrderInput): ManualOrderInput {
    if (
        !input ||
        typeof input.symbol !== 'string' ||
        !/^[A-Z0-9][A-Z0-9._/-]{0,63}$/.test(input.symbol.trim().toUpperCase()) ||
        !['BUY', 'SELL'].includes(input.side) ||
        !['LIMIT', 'MARKET'].includes(input.orderType)
    )
        throw error('ORDER_SUBMISSION_INPUT_INVALID');
    return {
        symbol: input.symbol.trim().toUpperCase(),
        side: input.side,
        orderType: input.orderType,
        quantity: amount(input.quantity),
        ...(input.orderType === 'LIMIT' ? {price: amount(input.price)} : {}),
    };
}
function sameRequest(a: OrderSubmitRequest, b: OrderSubmitRequest) {
    return fields.every((field) => a[field] === b[field]);
}

/** 本地只保存原始请求及已定位的订单身份；正式状态始终来自服务端，不另建订单事实源。 */
export function readOrderSubmission(owner: number | undefined): OrderSubmissionStorage {
    if (!positiveId(owner)) return {kind: 'none'};
    try {
        const raw = localStorage.getItem(key(owner));
        if (raw === null) return {kind: 'none'};
        if (raw.length > 65536) return {kind: 'unavailable'};
        const value = JSON.parse(raw) as PendingOrderSubmission;
        const request = value?.request;
        if (
            !value ||
            value.ownerUserId !== owner ||
            !positiveId(value.legacyAccountId) ||
            typeof value.accountAlias !== 'string' ||
            value.accountAlias.length > 256 ||
            !request ||
            !positiveId(request.accountId) ||
            !['OKX', 'BINANCE'].includes(request.venue) ||
            !/^manual-[0-9a-f]{32}$/.test(request.clientOrderId) ||
            request.strategyRunId !== undefined ||
            Object.keys(request).some(
                (field) => !fields.includes(field as (typeof fields)[number]),
            ) ||
            !sameRequest(request, {...request, ...normalizeManualOrder(request)}) ||
            (request.orderType === 'MARKET' && request.price !== undefined) ||
            !['SUBMITTING', 'UNKNOWN', 'CONFIRMED'].includes(value.phase) ||
            !Number.isSafeInteger(value.recordedAt) ||
            value.recordedAt <= 0 ||
            (value.phase === 'CONFIRMED' &&
                (typeof value.orderId !== 'string' ||
                    !/^ord-[A-Za-z0-9-]{1,60}$/.test(value.orderId)))
        )
            return {kind: 'unavailable'};
        return {
            kind: 'saved',
            pending: {
                ...value,
                request: {...request},
                phase:
                    value.phase === 'CONFIRMED'
                        ? 'CONFIRMED'
                        : inFlight.has(identity(value))
                          ? 'SUBMITTING'
                          : 'UNKNOWN',
            },
        };
    } catch {
        return {kind: 'unavailable'};
    }
}

function scope(pending?: PendingOrderSubmission, write = false) {
    const auth = useAuthStore.getState();
    const context = useAccountContextStore.getState();
    const user = auth.currentUser;
    if (
        !user?.authenticated ||
        !positiveId(user.userId) ||
        !auth.accessToken ||
        (write &&
            !user.roles.some((role) =>
                ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
            ))
    )
        throw error('ORDER_SUBMISSION_SCOPE_UNAVAILABLE');
    if (
        !positiveId(context.selectedExchangeAccountId) ||
        !positiveId(context.legacyAccountId) ||
        context.tradeEnv !== 'SIM' ||
        !context.exchangeCode ||
        !['OKX', 'BINANCE'].includes(context.exchangeCode)
    )
        throw error('ORDER_SUBMISSION_ACCOUNT_UNAVAILABLE');
    if (
        pending &&
        (pending.ownerUserId !== user.userId ||
            pending.request.accountId !== context.selectedExchangeAccountId ||
            pending.legacyAccountId !== context.legacyAccountId ||
            pending.request.venue !== context.exchangeCode)
    )
        throw error('ORDER_SUBMISSION_SCOPE_CHANGED');
    return {
        owner: user.userId,
        token: auth.accessToken,
        accountId: context.selectedExchangeAccountId,
        legacyAccountId: context.legacyAccountId,
        venue: context.exchangeCode,
        alias: context.accountAlias ?? '',
    };
}
type Scope = ReturnType<typeof scope>;
function requireSession(sent: Scope, pending?: PendingOrderSubmission, write = false) {
    const current = scope(pending, write);
    if (
        current.owner !== sent.owner ||
        current.token !== sent.token ||
        current.accountId !== sent.accountId ||
        current.legacyAccountId !== sent.legacyAccountId ||
        current.venue !== sent.venue
    )
        throw error('ORDER_SUBMISSION_SCOPE_CHANGED');
}
function original(pending: PendingOrderSubmission) {
    const stored = readOrderSubmission(pending.ownerUserId);
    if (
        stored.kind !== 'saved' ||
        stored.pending.legacyAccountId !== pending.legacyAccountId ||
        !sameRequest(stored.pending.request, pending.request)
    )
        throw error('ORDER_SUBMISSION_STORAGE_UNAVAILABLE');
    return stored.pending;
}
function retain(pending: PendingOrderSubmission) {
    try {
        const raw = JSON.stringify(pending);
        localStorage.setItem(key(pending.ownerUserId), raw);
        if (localStorage.getItem(key(pending.ownerUserId)) !== raw)
            throw new Error('storage readback mismatch');
    } catch {
        throw error('ORDER_SUBMISSION_STORAGE_UNAVAILABLE');
    }
    notify();
}
async function coordinated<T>(owner: number, work: () => Promise<T>) {
    if (typeof navigator === 'undefined' || !navigator.locks?.request)
        throw error('ORDER_SUBMISSION_COORDINATION_UNAVAILABLE');
    return navigator.locks.request(
        key(owner),
        {mode: 'exclusive', ifAvailable: true},
        async (lock) => {
            if (!lock) throw error('ORDER_SUBMISSION_PENDING');
            return work();
        },
    );
}
function normalizedDecimal(value: string) {
    const [integer, fraction = ''] = value.split('.');
    return integer.replace(/^0+(?=\d)/, '') + '.' + fraction.replace(/0+$/, '');
}

/** Jackson可将合法numeric输出为指数词法；仅对正式响应精确展开，仍校验38位精度和8位小数。 */
function formalDecimal(value: unknown): string {
    if (typeof value !== 'string' || value.length > 128)
        throw error('ORDER_SUBMISSION_RESPONSE_INVALID');
    const match = /^(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d{1,3}))?$/.exec(value);
    if (!match) throw error('ORDER_SUBMISSION_RESPONSE_INVALID');
    const exponent = Number(match[3] ?? 0);
    if (Math.abs(exponent) > 128) throw error('ORDER_SUBMISSION_RESPONSE_INVALID');
    let digits = (match[1] + (match[2] ?? '')).replace(/^0+/, '');
    let scale = (match[2] ?? '').length - exponent;
    while (scale > 0 && digits.endsWith('0')) {
        digits = digits.slice(0, -1);
        scale--;
    }
    if (!digits || scale > 8 || scale < -30 || digits.length - scale > 30)
        throw error('ORDER_SUBMISSION_RESPONSE_INVALID');
    const split = digits.length - scale;
    const expanded =
        scale <= 0
            ? digits + '0'.repeat(-scale)
            : split > 0
              ? digits.slice(0, split) + '.' + digits.slice(split)
              : '0.' + '0'.repeat(-split) + digits;
    return normalizedDecimal(amount(expanded));
}

/** 只有完整原身份、范围及精确经济输入匹配的正式订单，才能解除未知状态；回执文本不是依据。 */
export function confirmOriginalOrder(pending: PendingOrderSubmission, order: OrderView): OrderView {
    const request = pending.request;
    if (
        !order ||
        typeof order.orderId !== 'string' ||
        !/^ord-[A-Za-z0-9-]{1,60}$/.test(order.orderId) ||
        (pending.orderId !== undefined && pending.orderId !== order.orderId) ||
        order.accountId !== pending.legacyAccountId ||
        order.clientOrderId !== request.clientOrderId ||
        order.venue !== request.venue ||
        order.tradeEnv !== 'SIM' ||
        order.symbol !== request.symbol ||
        order.side !== request.side ||
        order.type !== request.orderType ||
        !tradingOrderStatuses.some((status) => status === order.status) ||
        formalDecimal(order.quantity) !== normalizedDecimal(request.quantity) ||
        (request.orderType === 'LIMIT'
            ? formalDecimal(order.price) !== normalizedDecimal(request.price!)
            : order.price !== null)
    )
        throw error('ORDER_SUBMISSION_RESPONSE_INVALID');
    return order;
}
async function lookup(pending: PendingOrderSubmission, sent: Scope) {
    const order = await tradingWorkbenchApi.getOrderByClientIdentity(
        pending.request.accountId,
        pending.request.clientOrderId,
    );
    requireSession(sent, pending);
    original(pending);
    confirmOriginalOrder(pending, order);
    retain({...pending, phase: 'CONFIRMED', orderId: order.orderId});
    return order;
}
async function execute(pending: PendingOrderSubmission, sent: Scope) {
    original(pending);
    inFlight.add(identity(pending));
    try {
        retain({...pending, phase: 'SUBMITTING'});
        // 正式账户再次确认映射与启用状态；不信任页面或历史本地缓存的交易范围。
        const account = await accountsApi.detail(pending.request.accountId);
        requireSession(sent, pending, true);
        if (
            account.exchangeAccountId !== pending.request.accountId ||
            account.legacyAccountId !== pending.legacyAccountId ||
            account.exchangeCode !== pending.request.venue ||
            account.tradeEnv !== 'SIM' ||
            account.status !== 'ACTIVE'
        )
            throw error('ORDER_SUBMISSION_ACCOUNT_UNAVAILABLE');
        original(pending);
        await tradingWorkbenchApi.placeOrder({...pending.request});
        requireSession(sent, pending, true);
        return await lookup(pending, sent);
    } catch (failure) {
        try {
            // 原主体退出或会话变化后也只保留原记录为未知，绝不修改新主体/新身份的记录。
            const stored = original(pending);
            if (stored.phase !== 'CONFIRMED') retain({...stored, phase: 'UNKNOWN'});
        } catch {
            /* 提交前的原输入仍保留；存储损坏时禁止换号重发。 */
        }
        throw failure;
    } finally {
        inFlight.delete(identity(pending));
        notify();
    }
}
const missingIdentity = (pending: PendingOrderSubmission, sent: Scope) =>
    `${identity(pending)}:${sent.token}:${sent.accountId}`;

export const orderSubmission = {
    async submit(input: ManualOrderInput) {
        const sent = scope(undefined, true);
        const normalized = normalizeManualOrder(input);
        return coordinated(sent.owner, async () => {
            requireSession(sent, undefined, true);
            if (readOrderSubmission(sent.owner).kind !== 'none')
                throw error('ORDER_SUBMISSION_PENDING');
            if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function')
                throw error('ORDER_SUBMISSION_IDENTITY_UNAVAILABLE');
            const uuid = crypto.randomUUID();
            if (
                !/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/.test(uuid) ||
                !/[1-9a-f]/.test(uuid)
            )
                throw error('ORDER_SUBMISSION_IDENTITY_UNAVAILABLE');
            const pending: PendingOrderSubmission = {
                ownerUserId: sent.owner,
                legacyAccountId: sent.legacyAccountId,
                accountAlias: sent.alias,
                request: {
                    accountId: sent.accountId,
                    venue: sent.venue,
                    clientOrderId: 'manual-' + uuid.replaceAll('-', ''),
                    ...normalized,
                },
                phase: 'SUBMITTING',
                recordedAt: Date.now(),
            };
            missing = null;
            retain(pending);
            return execute(pending, sent);
        });
    },
    async lookup(pending: PendingOrderSubmission) {
        const sent = scope(pending);
        return coordinated(sent.owner, async () => {
            requireSession(sent, pending);
            original(pending);
            missing = null;
            try {
                return await lookup(pending, sent);
            } catch (failure) {
                requireSession(sent, pending);
                if ((failure as AppApiError).status === 404)
                    missing = missingIdentity(pending, sent);
                throw failure;
            }
        });
    },
    async resume(pending: PendingOrderSubmission) {
        const sent = scope(pending, true);
        return coordinated(sent.owner, async () => {
            requireSession(sent, pending, true);
            const stored = original(pending);
            if (stored.phase === 'CONFIRMED' || missing !== missingIdentity(pending, sent))
                throw error('ORDER_SUBMISSION_LOOKUP_REQUIRED');
            missing = null;
            return execute(stored, sent);
        });
    },
    async acknowledge(pending: PendingOrderSubmission) {
        const sent = scope(pending);
        return coordinated(sent.owner, async () => {
            requireSession(sent, pending);
            if (original(pending).phase !== 'CONFIRMED')
                throw error('ORDER_SUBMISSION_LOOKUP_REQUIRED');
            localStorage.removeItem(key(sent.owner));
            if (localStorage.getItem(key(sent.owner)) !== null)
                throw error('ORDER_SUBMISSION_STORAGE_UNAVAILABLE');
            notify();
        });
    },
};
