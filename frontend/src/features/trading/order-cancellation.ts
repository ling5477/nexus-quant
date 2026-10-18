import {accountsApi} from '@/features/accounts/api/accounts';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import {tradingWorkbenchApi} from './api/trading-workbench';
import {tradingOrderStatuses} from './trading-presentation';
import type {
    OperationTriggerResponse,
    OrderCancelRequestBody,
    OrderView,
} from './types/trading-workbench';

const cancelable = new Set(['SENT', 'ACCEPTED', 'ACKED', 'PARTIALLY_FILLED', 'CANCEL_REJECTED']);
const terminal = new Set(['CANCELLED', 'FILLED', 'REJECTED', 'RISK_REJECTED', 'FAILED']);
const positiveId = (value: unknown): value is number =>
    typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const orderIdentity = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$/;
const key = (owner: number) => `nq:order-cancellations:v1:${owner}`;
const inFlight = new Set<string>();
export const orderCancellationChanged = 'nq:order-cancellation-changed';
const notify = () => {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(orderCancellationChanged));
};
const error = (code: string): AppApiError =>
    Object.assign(new Error(code), {status: 0, code, fieldErrors: []});

export interface CancellationTarget {
    orderId: string;
    accountId: number;
    clientOrderId: string;
    venue: string;
    tradeEnv: 'SIM';
    symbol: string;
}
export interface PendingOrderCancellation {
    ownerUserId: number;
    accountAlias: string;
    target: CancellationTarget;
    request: {orderId: string; accountId: number; reason: string};
    phase: 'SUBMITTING' | 'UNKNOWN';
    recordedAt: number;
    receipt?: OperationTriggerResponse;
}
export type OrderCancellationStorage =
    | {kind: 'unavailable'}
    | {kind: 'saved'; records: PendingOrderCancellation[]};
interface CancellationSession {
    owner: number;
    token: string;
    accountId: number;
    legacyAccountId: number;
    venue: string;
}
export interface PreparedOrderCancellation {
    session: CancellationSession;
    pending: PendingOrderCancellation;
}

/** 仅控制入口可用性，正式状态迁移及执行校验仍由canonical服务决定。 */
export function canRequestCancellation(order: OrderView): boolean {
    return order.tradeEnv === 'SIM' && cancelable.has(order.status);
}
function sameTarget(a: CancellationTarget, b: CancellationTarget) {
    return (
        a.orderId === b.orderId &&
        a.accountId === b.accountId &&
        a.clientOrderId === b.clientOrderId &&
        a.venue === b.venue &&
        a.tradeEnv === b.tradeEnv &&
        a.symbol === b.symbol
    );
}
function validTarget(value: CancellationTarget): boolean {
    return (
        !!value &&
        typeof value.orderId === 'string' &&
        orderIdentity.test(value.orderId) &&
        positiveId(value.accountId) &&
        typeof value.clientOrderId === 'string' &&
        value.clientOrderId.length > 0 &&
        value.clientOrderId.length <= 256 &&
        ['OKX', 'BINANCE'].includes(value.venue) &&
        value.tradeEnv === 'SIM' &&
        typeof value.symbol === 'string' &&
        value.symbol.length > 0 &&
        value.symbol.length <= 64
    );
}
function validPending(item: PendingOrderCancellation, owner: number): boolean {
    return (
        !!item &&
        item.ownerUserId === owner &&
        typeof item.accountAlias === 'string' &&
        item.accountAlias.length <= 256 &&
        validTarget(item.target) &&
        !!item.request &&
        item.request.orderId === item.target.orderId &&
        positiveId(item.request.accountId) &&
        typeof item.request.reason === 'string' &&
        !!item.request.reason.trim() &&
        item.request.reason === item.request.reason.trim() &&
        item.request.reason.length <= 255 &&
        Object.keys(item.request).every((field) =>
            ['orderId', 'accountId', 'reason'].includes(field),
        ) &&
        ['SUBMITTING', 'UNKNOWN'].includes(item.phase) &&
        Number.isSafeInteger(item.recordedAt) &&
        item.recordedAt > 0 &&
        (item.receipt === undefined || receiptStatus(item.receipt, item.target) !== null)
    );
}

/** 原回执只确认命令回复身份；不能把CANCEL_REQUESTED或任意200当作最终撤单。 */
function receiptStatus(value: OperationTriggerResponse, target: CancellationTarget): string | null {
    if (
        !value ||
        value.action !== 'cancelOrder' ||
        typeof value.detail !== 'string' ||
        value.detail.length > 4096 ||
        typeof value.traceId !== 'string' ||
        !value.traceId
    )
        return null;
    const ids = [...value.detail.matchAll(/(?:^|,)\s*order_id=([^,]*)/g)];
    const states = [...value.detail.matchAll(/(?:^|,)\s*status=([^,]*)/g)];
    const state = states.length === 1 ? states[0][1].trim() : null;
    return ids.length === 1 &&
        ids[0][1].trim() === target.orderId &&
        state &&
        tradingOrderStatuses.some((candidate) => candidate === state)
        ? state
        : null;
}

/** 每主体最多保留32条待核对撤单；损坏、超限或不可读都不能当成没有原操作。 */
export function readOrderCancellations(owner?: number): OrderCancellationStorage {
    if (!positiveId(owner)) return {kind: 'saved', records: []};
    try {
        const raw = localStorage.getItem(key(owner));
        if (raw === null) return {kind: 'saved', records: []};
        if (raw.length > 65536) return {kind: 'unavailable'};
        const values = JSON.parse(raw) as PendingOrderCancellation[];
        if (!Array.isArray(values) || values.length > 32) return {kind: 'unavailable'};
        const identities = new Set<string>();
        for (const item of values) {
            if (!validPending(item, owner) || identities.has(item.target.orderId))
                return {kind: 'unavailable'};
            identities.add(item.target.orderId);
        }
        return {
            kind: 'saved',
            records: values.map((item) => ({
                ...item,
                phase: inFlight.has(`${owner}:${item.target.orderId}`) ? 'SUBMITTING' : 'UNKNOWN',
            })),
        };
    } catch {
        return {kind: 'unavailable'};
    }
}
function saved(owner: number) {
    const value = readOrderCancellations(owner);
    if (value.kind !== 'saved') throw error('ORDER_CANCELLATION_STORAGE_UNAVAILABLE');
    return value.records;
}
function same(a: PendingOrderCancellation, b: PendingOrderCancellation) {
    return (
        a.ownerUserId === b.ownerUserId &&
        a.recordedAt === b.recordedAt &&
        a.request.accountId === b.request.accountId &&
        a.request.reason === b.request.reason &&
        sameTarget(a.target, b.target)
    );
}
function original(pending: PendingOrderCancellation) {
    const found = saved(pending.ownerUserId).find((item) => same(item, pending));
    if (!found) throw error('ORDER_CANCELLATION_ORIGINAL_CHANGED');
    return found;
}
function retain(owner: number, values: PendingOrderCancellation[]) {
    try {
        const raw = JSON.stringify(values);
        if (raw.length > 65536 || values.length > 32) throw new Error('bounded storage exceeded');
        localStorage.setItem(key(owner), raw);
        if (localStorage.getItem(key(owner)) !== raw) throw new Error('storage readback mismatch');
    } catch {
        throw error('ORDER_CANCELLATION_STORAGE_UNAVAILABLE');
    }
    notify();
}
function replace(pending: PendingOrderCancellation) {
    const values = saved(pending.ownerUserId);
    const index = values.findIndex((item) => same(item, pending));
    if (index < 0) throw error('ORDER_CANCELLATION_ORIGINAL_CHANGED');
    values[index] = pending;
    retain(pending.ownerUserId, values);
}
function scope(pending?: PendingOrderCancellation, writing = false): CancellationSession {
    const auth = useAuthStore.getState();
    const account = useAccountContextStore.getState();
    const user = auth.currentUser;
    if (
        !user?.authenticated ||
        !positiveId(user.userId) ||
        !auth.accessToken ||
        (writing &&
            !user.roles.some((role) =>
                ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
            ))
    )
        throw error('ORDER_CANCELLATION_SCOPE_UNAVAILABLE');
    if (
        !positiveId(account.selectedExchangeAccountId) ||
        !positiveId(account.legacyAccountId) ||
        account.tradeEnv !== 'SIM' ||
        !account.exchangeCode ||
        !['OKX', 'BINANCE'].includes(account.exchangeCode)
    )
        throw error('ORDER_CANCELLATION_ACCOUNT_UNAVAILABLE');
    if (
        pending &&
        (pending.ownerUserId !== user.userId ||
            pending.request.accountId !== account.selectedExchangeAccountId ||
            pending.target.accountId !== account.legacyAccountId ||
            pending.target.venue !== account.exchangeCode)
    )
        throw error('ORDER_CANCELLATION_SCOPE_CHANGED');
    return {
        owner: user.userId,
        token: auth.accessToken,
        accountId: account.selectedExchangeAccountId,
        legacyAccountId: account.legacyAccountId,
        venue: account.exchangeCode,
    };
}
function requireSession(
    sent: CancellationSession,
    pending: PendingOrderCancellation,
    writing = false,
) {
    const current = scope(pending, writing);
    if (
        sent.owner !== current.owner ||
        sent.token !== current.token ||
        sent.accountId !== current.accountId ||
        sent.legacyAccountId !== current.legacyAccountId ||
        sent.venue !== current.venue
    )
        throw error('ORDER_CANCELLATION_SCOPE_CHANGED');
}
async function coordinated<T>(owner: number, work: () => Promise<T>) {
    if (typeof navigator === 'undefined' || !navigator.locks?.request)
        throw error('ORDER_CANCELLATION_COORDINATION_UNAVAILABLE');
    return navigator.locks.request(
        key(owner),
        {mode: 'exclusive', ifAvailable: true},
        async (lock) => {
            if (!lock) throw error('ORDER_CANCELLATION_BUSY');
            return work();
        },
    );
}
function confirmTarget(pending: PendingOrderCancellation, order: OrderView) {
    if (
        !order ||
        !sameTarget(pending.target, order as CancellationTarget) ||
        !tradingOrderStatuses.some((status) => status === order.status)
    )
        throw error('ORDER_CANCELLATION_RESPONSE_INVALID');
    return order;
}

/** 确认框打开时就冻结会话和原订单；确认期间切账户或重登录，不能套用新身份发出旧动作。 */
export function prepareOrderCancellation(
    order: OrderView,
    reason: string,
): PreparedOrderCancellation {
    const sent = scope(undefined, true);
    if (
        !validTarget(order as CancellationTarget) ||
        order.accountId !== sent.legacyAccountId ||
        order.venue !== sent.venue ||
        !canRequestCancellation(order)
    )
        throw error('ORDER_CANCELLATION_STATE_UNAVAILABLE');
    if (typeof reason !== 'string' || !reason.trim() || reason.trim().length > 255)
        throw error('ORDER_CANCELLATION_REASON_INVALID');
    const pending: PendingOrderCancellation = {
        ownerUserId: sent.owner,
        accountAlias: useAccountContextStore.getState().accountAlias ?? '',
        target: {
            orderId: order.orderId,
            accountId: order.accountId,
            clientOrderId: order.clientOrderId,
            venue: order.venue,
            tradeEnv: 'SIM',
            symbol: order.symbol,
        },
        request: {orderId: order.orderId, accountId: sent.accountId, reason: reason.trim()},
        phase: 'SUBMITTING',
        recordedAt: Date.now(),
    };
    return {session: sent, pending};
}

/** 只在正式终态或有原命令回执的明确撤单拒绝时允许清理；未知与404不能释放原身份。 */
export function cancellationCanBeAcknowledged(
    pending: PendingOrderCancellation,
    order: OrderView,
): boolean {
    confirmTarget(pending, order);
    return (
        terminal.has(order.status) ||
        (order.status === 'CANCEL_REJECTED' &&
            pending.receipt !== undefined &&
            receiptStatus(pending.receipt, pending.target) === 'CANCEL_REJECTED')
    );
}

export const orderCancellation = {
    async submit(prepared: PreparedOrderCancellation) {
        // 从冻结值复制请求，调用方后续编辑表单不能修改已确认的目标或原因。
        const pending: PendingOrderCancellation = structuredClone(prepared.pending);
        const sent = {...prepared.session};
        if (
            !validPending(pending, sent.owner) ||
            pending.receipt !== undefined ||
            pending.phase !== 'SUBMITTING'
        )
            throw error('ORDER_CANCELLATION_ORIGINAL_CHANGED');
        requireSession(sent, pending, true);
        return coordinated(sent.owner, async () => {
            requireSession(sent, pending, true);
            const values = saved(sent.owner);
            if (values.some((item) => item.target.orderId === pending.target.orderId))
                throw error('ORDER_CANCELLATION_PENDING');
            if (values.length >= 32) throw error('ORDER_CANCELLATION_CAPACITY');
            const account = await accountsApi.detail(pending.request.accountId);
            requireSession(sent, pending, true);
            if (
                account.status !== 'ACTIVE' ||
                account.exchangeAccountId !== sent.accountId ||
                account.legacyAccountId !== sent.legacyAccountId ||
                account.exchangeCode !== sent.venue ||
                account.tradeEnv !== 'SIM'
            )
                throw error('ORDER_CANCELLATION_ACCOUNT_UNAVAILABLE');
            const current = await tradingWorkbenchApi.getOrder(pending.target.orderId);
            requireSession(sent, pending, true);
            confirmTarget(pending, current);
            if (!canRequestCancellation(current))
                throw error('ORDER_CANCELLATION_STATE_UNAVAILABLE');
            inFlight.add(`${sent.owner}:${pending.target.orderId}`);
            try {
                retain(sent.owner, [...values, pending]);
                requireSession(sent, pending, true);
                const receipt = await tradingWorkbenchApi.cancelOrder({
                    ...pending.request,
                } satisfies OrderCancelRequestBody);
                requireSession(sent, pending, true);
                if (receiptStatus(receipt, pending.target) === null)
                    throw error('ORDER_CANCELLATION_RESPONSE_INVALID');
                replace({...original(pending), receipt, phase: 'UNKNOWN'});
                const order = await tradingWorkbenchApi.getOrder(pending.target.orderId);
                requireSession(sent, pending);
                original(pending);
                return confirmTarget(pending, order);
            } catch (failure) {
                try {
                    replace({...original(pending), phase: 'UNKNOWN'});
                } catch {
                    /* 原请求已保存；存储失败也不能自动重发。 */
                }
                throw failure;
            } finally {
                inFlight.delete(`${sent.owner}:${pending.target.orderId}`);
                notify();
            }
        });
    },
    async lookup(pending: PendingOrderCancellation) {
        const sent = scope(pending);
        return coordinated(sent.owner, async () => {
            requireSession(sent, pending);
            original(pending);
            const order = await tradingWorkbenchApi.getOrder(pending.target.orderId);
            requireSession(sent, pending);
            original(pending);
            return confirmTarget(pending, order);
        });
    },
    async acknowledge(pending: PendingOrderCancellation) {
        const sent = scope(pending);
        return coordinated(sent.owner, async () => {
            requireSession(sent, pending);
            const stored = original(pending);
            const order = await tradingWorkbenchApi.getOrder(pending.target.orderId);
            requireSession(sent, pending);
            original(pending);
            if (!cancellationCanBeAcknowledged(stored, order))
                throw error('ORDER_CANCELLATION_UNRESOLVED');
            retain(
                sent.owner,
                saved(sent.owner).filter((item) => !same(item, pending)),
            );
            return order;
        });
    },
};
