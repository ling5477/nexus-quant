import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {accountsApi} from '@/features/accounts/api/accounts';
import {tradingWorkbenchApi} from '@/features/trading/api/trading-workbench';
import {
    cancellationCanBeAcknowledged,
    canRequestCancellation,
    orderCancellation,
    prepareOrderCancellation,
    readOrderCancellations,
    type PendingOrderCancellation,
} from '@/features/trading/order-cancellation';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {CurrentUser} from '@/types/auth';
import type {OperationTriggerResponse, OrderView} from '@/features/trading/types/trading-workbench';

const data = new Map<string, string>();
const locks = new Set<string>();
const storageKey = 'nq:order-cancellations:v1:17';
const user: CurrentUser = {
    userId: 17,
    username: 'cancel-owner',
    authenticated: true,
    roles: ['OPERATOR'],
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const account = {
    exchangeAccountId: 501,
    legacyAccountId: 101,
    exchangeCode: 'OKX',
    tradeEnv: 'SIM',
    accountAlias: 'Owned SIM',
    status: 'ACTIVE',
    isDefault: true,
    externalAccountRef: null,
};
const order = (orderId = 'ord-original'): OrderView => ({
    orderId,
    accountId: 101,
    clientOrderId: ' client-exact ' + orderId,
    venue: 'OKX',
    tradeEnv: 'SIM',
    symbol: 'BTC-USDT',
    side: 'BUY',
    type: 'LIMIT',
    price: '9007199254740993.12345678',
    quantity: '0.00000001',
    status: 'SENT',
    externalOrderId: null,
    createdAt: null,
    updatedAt: null,
    traceId: 'trace',
});
const response = (
    orderId = 'ord-original',
    status = 'CANCEL_REQUESTED',
): OperationTriggerResponse => ({
    action: 'cancelOrder',
    traceId: 'cancel-trace',
    detail: `order_id=${orderId}, status=${status}, idempotent_hit=false`,
});
const pending = (id = 'ord-original'): PendingOrderCancellation => {
    const value = readOrderCancellations(17);
    if (value.kind !== 'saved') throw new Error('saved records expected');
    const record = value.records.find((row) => row.target.orderId === id);
    if (!record) throw new Error('original expected');
    return record;
};
beforeEach(() => {
    data.clear();
    locks.clear();
    vi.stubGlobal('window', {dispatchEvent: vi.fn()});
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => data.get(key) ?? null,
        setItem: (key: string, value: string) => data.set(key, value),
        removeItem: (key: string) => data.delete(key),
    });
    vi.stubGlobal('navigator', {
        locks: {
            request: async (
                name: string,
                _options: unknown,
                callback: (lock: unknown) => Promise<unknown>,
            ) => {
                if (locks.has(name)) return callback(null);
                locks.add(name);
                try {
                    return await callback({name});
                } finally {
                    locks.delete(name);
                }
            },
        },
    });
    useAuthStore.setState({
        currentUser: {...user},
        accessToken: 'cancel-session-a',
        tokenType: 'Bearer',
    });
    useAccountContextStore.getState().setSelectedAccount(account);
    vi.spyOn(accountsApi, 'detail').mockResolvedValue(account);
    vi.spyOn(tradingWorkbenchApi, 'getOrder').mockImplementation(async (id) => order(id));
    vi.spyOn(tradingWorkbenchApi, 'cancelOrder').mockImplementation(async (request) =>
        response(request.orderId),
    );
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null, accessToken: null});
    useAccountContextStore.getState().clearAccountContext();
});
const prepared = (id = 'ord-original') =>
    prepareOrderCancellation(order(id), '  user cancellation  ');
async function loseResponse(id = 'ord-original') {
    vi.mocked(tradingWorkbenchApi.cancelOrder).mockRejectedValueOnce({
        status: 0,
        code: 'NETWORK_ERROR',
    });
    await orderCancellation.submit(prepared(id)).catch(() => undefined);
    return pending(id);
}

describe('正式原订单撤单与只读未知恢复', () => {
    it('POST之前已保存精确原身份及原因，canonical与legacy不混用，token不持久化', async () => {
        vi.mocked(tradingWorkbenchApi.cancelOrder).mockImplementationOnce(async (request) => {
            expect(request).toEqual({
                orderId: 'ord-original',
                accountId: 501,
                reason: 'user cancellation',
            });
            expect(pending().request).toEqual(request);
            expect(pending().target.accountId).toBe(101);
            expect(pending().target.clientOrderId).toBe(' client-exact ord-original');
            expect(data.get(storageKey)).not.toContain('cancel-session-a');
            return response();
        });
        await orderCancellation.submit(prepared());
        expect(pending().phase).toBe('UNKNOWN');
        expect(pending().receipt?.detail).toContain('CANCEL_REQUESTED');
    });
    it('刷新只读本地原操作，不自动查询或重发；同一原单不能再次撤单', async () => {
        await loseResponse();
        const reads = vi.mocked(tradingWorkbenchApi.getOrder).mock.calls.length;
        expect(readOrderCancellations(17).kind).toBe('saved');
        expect(tradingWorkbenchApi.getOrder).toHaveBeenCalledTimes(reads);
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_PENDING',
        });
        expect(tradingWorkbenchApi.cancelOrder).toHaveBeenCalledTimes(1);
    });
    it('一笔UNKNOWN不丢失，也不阻塞另一原订单独立撤单', async () => {
        await loseResponse('ord-a');
        await loseResponse('ord-b');
        const value = readOrderCancellations(17);
        expect(value.kind === 'saved' && value.records.map((row) => row.target.orderId)).toEqual([
            'ord-a',
            'ord-b',
        ]);
        expect(tradingWorkbenchApi.cancelOrder).toHaveBeenCalledTimes(2);
    });
    it('查询404仍保留原记录且绝不重复发送', async () => {
        const value = await loseResponse();
        vi.mocked(tradingWorkbenchApi.getOrder).mockRejectedValueOnce({status: 404});
        await expect(orderCancellation.lookup(value)).rejects.toMatchObject({status: 404});
        expect(pending().phase).toBe('UNKNOWN');
        expect(tradingWorkbenchApi.cancelOrder).toHaveBeenCalledTimes(1);
    });
    it('CANCEL_REQUESTED不是最终撤销，不能清理或解除重复保护', async () => {
        const value = await loseResponse();
        vi.mocked(tradingWorkbenchApi.getOrder).mockResolvedValue({
            ...order(),
            status: 'CANCEL_REQUESTED',
        });
        expect(cancellationCanBeAcknowledged(value, await orderCancellation.lookup(value))).toBe(
            false,
        );
        await expect(orderCancellation.acknowledge(value)).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_UNRESOLVED',
        });
        expect(pending()).toBeDefined();
    });
    it.each(['CANCELLED', 'FILLED', 'REJECTED', 'RISK_REJECTED', 'FAILED'])(
        '正式结束状态%s可在再次读取后清理，期间不发新撤单',
        async (status) => {
            const value = await loseResponse();
            vi.mocked(tradingWorkbenchApi.getOrder).mockResolvedValue({...order(), status});
            const result = await orderCancellation.lookup(value);
            expect(cancellationCanBeAcknowledged(value, result)).toBe(true);
            await orderCancellation.acknowledge(value);
            expect(readOrderCancellations(17)).toEqual({kind: 'saved', records: []});
            expect(tradingWorkbenchApi.cancelOrder).toHaveBeenCalledTimes(1);
        },
    );
    it('曾显示终态后再次读取变为非终态，不能凭旧显示清理', async () => {
        const value = await loseResponse();
        vi.mocked(tradingWorkbenchApi.getOrder).mockResolvedValueOnce({
            ...order(),
            status: 'CANCELLED',
        });
        expect(cancellationCanBeAcknowledged(value, await orderCancellation.lookup(value))).toBe(
            true,
        );
        await expect(orderCancellation.acknowledge(value)).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_UNRESOLVED',
        });
    });
    it('无原命令回执的CANCEL_REJECTED不能猜成当前这次失败后释放', async () => {
        const value = await loseResponse();
        vi.mocked(tradingWorkbenchApi.getOrder).mockResolvedValue({
            ...order(),
            status: 'CANCEL_REJECTED',
        });
        await expect(orderCancellation.acknowledge(value)).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_UNRESOLVED',
        });
    });
    it('原命令明确返回CANCEL_REJECTED且当前正式状态相同，才允许确认清理', async () => {
        vi.mocked(tradingWorkbenchApi.cancelOrder).mockResolvedValueOnce(
            response('ord-original', 'CANCEL_REJECTED'),
        );
        await orderCancellation.submit(prepared());
        vi.mocked(tradingWorkbenchApi.getOrder).mockResolvedValue({
            ...order(),
            status: 'CANCEL_REJECTED',
        });
        await orderCancellation.acknowledge(pending());
        expect(readOrderCancellations(17)).toEqual({kind: 'saved', records: []});
    });
    it('存储配额失败或读回失败发生在POST之前', async () => {
        vi.spyOn(localStorage, 'setItem').mockImplementationOnce(() => {
            throw new Error('quota');
        });
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_STORAGE_UNAVAILABLE',
        });
        vi.spyOn(localStorage, 'setItem').mockImplementation(() => undefined);
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_STORAGE_UNAVAILABLE',
        });
        expect(tradingWorkbenchApi.cancelOrder).not.toHaveBeenCalled();
    });
    it('损坏存储和没有安全锁都阻止新POST', async () => {
        data.set(storageKey, '[broken');
        expect(readOrderCancellations(17)).toEqual({kind: 'unavailable'});
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_STORAGE_UNAVAILABLE',
        });
        data.clear();
        vi.stubGlobal('navigator', {});
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_COORDINATION_UNAVAILABLE',
        });
        expect(tradingWorkbenchApi.cancelOrder).not.toHaveBeenCalled();
    });
    it('同一主体并发操作只允许持锁者执行，成功及失败都释放锁', async () => {
        let release!: (value: typeof account) => void;
        vi.mocked(accountsApi.detail).mockReturnValueOnce(
            new Promise((resolve) => {
                release = resolve;
            }),
        );
        const first = orderCancellation.submit(prepared('ord-a'));
        await expect(orderCancellation.submit(prepared('ord-b'))).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_BUSY',
        });
        release(account);
        await first;
        await loseResponse('ord-b');
        expect(tradingWorkbenchApi.cancelOrder).toHaveBeenCalledTimes(2);
    });
    it('32笔上限保留全部原记录，不能淘汰UNKNOWN来发送第33笔', async () => {
        const sample = prepared().pending;
        data.set(
            storageKey,
            JSON.stringify(
                Array.from({length: 32}, (_, i) => ({
                    ...sample,
                    target: {...sample.target, orderId: 'ord-' + i},
                    request: {...sample.request, orderId: 'ord-' + i},
                })),
            ),
        );
        await expect(orderCancellation.submit(prepared('ord-33'))).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_CAPACITY',
        });
        expect(tradingWorkbenchApi.cancelOrder).not.toHaveBeenCalled();
        const value = readOrderCancellations(17);
        expect(value.kind === 'saved' && value.records.length).toBe(32);
    });
    it('确认期间换账户或同用户重新登录，都在任何HTTP之前拒绝', async () => {
        const value = prepared();
        useAuthStore.setState({accessToken: 'relogged'});
        await expect(orderCancellation.submit(value)).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_SCOPE_CHANGED',
        });
        useAuthStore.setState({accessToken: 'cancel-session-a'});
        useAccountContextStore
            .getState()
            .setSelectedAccount({...account, exchangeAccountId: 502, legacyAccountId: 102});
        await expect(orderCancellation.submit(value)).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_SCOPE_CHANGED',
        });
        expect(accountsApi.detail).not.toHaveBeenCalled();
        expect(tradingWorkbenchApi.cancelOrder).not.toHaveBeenCalled();
    });
    it.each(['LIVE', 'DEMO', 'UNKNOWN'])('环境%s不能准备撤单', (environment) => {
        useAccountContextStore.setState({tradeEnv: environment});
        expect(() => prepared()).toThrow('ORDER_CANCELLATION_ACCOUNT_UNAVAILABLE');
    });
    it('VIEWER可查本人原操作但不能准备或执行写请求', async () => {
        const value = prepared();
        const stored = await loseResponse();
        useAuthStore.setState({currentUser: {...user, roles: ['VIEWER']}});
        expect(() => prepared()).toThrow('ORDER_CANCELLATION_SCOPE_UNAVAILABLE');
        await expect(orderCancellation.submit(value)).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_SCOPE_UNAVAILABLE',
        });
        expect((await orderCancellation.lookup(stored)).orderId).toBe('ord-original');
        expect(tradingWorkbenchApi.cancelOrder).toHaveBeenCalledTimes(1);
    });
    it('正式账户停用或legacy变化时不POST', async () => {
        vi.mocked(accountsApi.detail).mockResolvedValueOnce({...account, status: 'DISABLED'});
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_ACCOUNT_UNAVAILABLE',
        });
        vi.mocked(accountsApi.detail).mockResolvedValueOnce({...account, legacyAccountId: 999});
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_ACCOUNT_UNAVAILABLE',
        });
        expect(tradingWorkbenchApi.cancelOrder).not.toHaveBeenCalled();
    });
    it.each(['FILLED', 'CANCEL_REQUESTED', 'CANCELLED', 'FUTURE_STATE'])(
        '确认后正式状态变为%s，阻止发送',
        async (status) => {
            const value = prepared();
            vi.mocked(tradingWorkbenchApi.getOrder).mockResolvedValueOnce({...order(), status});
            await expect(orderCancellation.submit(value)).rejects.toMatchObject({
                code:
                    status === 'FUTURE_STATE'
                        ? 'ORDER_CANCELLATION_RESPONSE_INVALID'
                        : 'ORDER_CANCELLATION_STATE_UNAVAILABLE',
            });
            expect(tradingWorkbenchApi.cancelOrder).not.toHaveBeenCalled();
            expect(readOrderCancellations(17)).toEqual({kind: 'saved', records: []});
        },
    );
    it.each([
        {accountId: 999},
        {clientOrderId: 'other-client'},
        {venue: 'BINANCE'},
        {tradeEnv: 'LIVE'},
        {orderId: 'ord-another'},
        {symbol: 'ETH-USDT'},
    ])('正式目标身份漂移%s时不POST', async (change) => {
        vi.mocked(tradingWorkbenchApi.getOrder).mockResolvedValueOnce({...order(), ...change});
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_RESPONSE_INVALID',
        });
        expect(tradingWorkbenchApi.cancelOrder).not.toHaveBeenCalled();
    });
    it('不能让request.orderId与已核对目标分离，也不能混入其他请求字段', async () => {
        const value = prepared();
        value.pending.request.orderId = 'ord-different';
        await expect(orderCancellation.submit(value)).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_ORIGINAL_CHANGED',
        });
        const other = prepared();
        Object.assign(other.pending.request, {clientOrderId: 'injected'});
        await expect(orderCancellation.submit(other)).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_ORIGINAL_CHANGED',
        });
        expect(tradingWorkbenchApi.cancelOrder).not.toHaveBeenCalled();
    });
    it.each([
        {action: 'placeOrder'},
        {detail: 'order_id=wrong, status=CANCELLED'},
        {detail: 'order_id=ord-original, status=CANCELLED, status=SENT'},
        {traceId: ''},
    ])('异常回执%s保留原操作，不推断成功', async (change) => {
        vi.mocked(tradingWorkbenchApi.cancelOrder).mockResolvedValueOnce({
            ...response(),
            ...change,
        });
        await expect(orderCancellation.submit(prepared())).rejects.toMatchObject({
            code: 'ORDER_CANCELLATION_RESPONSE_INVALID',
        });
        expect(pending().phase).toBe('UNKNOWN');
        expect(pending().receipt).toBeUndefined();
    });
    it('迟到写响应不属于新主体/新会话，原操作保留UNKNOWN不污染新主体', async () => {
        let release!: (value: OperationTriggerResponse) => void;
        vi.mocked(tradingWorkbenchApi.cancelOrder).mockReturnValueOnce(
            new Promise((resolve) => {
                release = resolve;
            }),
        );
        const first = orderCancellation.submit(prepared());
        await vi.waitFor(() => expect(tradingWorkbenchApi.cancelOrder).toHaveBeenCalledTimes(1));
        useAuthStore.setState({
            currentUser: {...user, userId: 18},
            accessToken: 'new-owner-session',
        });
        release(response());
        await expect(first).rejects.toMatchObject({code: 'ORDER_CANCELLATION_SCOPE_CHANGED'});
        expect(pending().phase).toBe('UNKNOWN');
        expect(pending().receipt).toBeUndefined();
        expect(readOrderCancellations(18)).toEqual({kind: 'saved', records: []});
    });
    it('跨会话迟到查询不被采用，也不能释放原操作', async () => {
        const value = await loseResponse();
        let release!: (value: OrderView) => void;
        vi.mocked(tradingWorkbenchApi.getOrder).mockReturnValueOnce(
            new Promise((resolve) => {
                release = resolve;
            }),
        );
        const result = orderCancellation.lookup(value);
        useAuthStore.setState({accessToken: 'relogged'});
        release({...order(), status: 'CANCELLED'});
        await expect(result).rejects.toMatchObject({code: 'ORDER_CANCELLATION_SCOPE_CHANGED'});
        expect(pending()).toBeDefined();
    });
    it('入口仅开放canonical允许进入CANCEL_REQUESTED的五个SIM状态', () => {
        for (const status of ['SENT', 'ACCEPTED', 'ACKED', 'PARTIALLY_FILLED', 'CANCEL_REJECTED'])
            expect(canRequestCancellation({...order(), status})).toBe(true);
        for (const status of [
            'NEW',
            'RISK_PASSED',
            'RISK_REJECTED',
            'VALIDATED',
            'SUBMITTING',
            'FILLED',
            'CANCEL_REQUESTED',
            'CANCELLED',
            'REJECTED',
            'FAILED',
            'FUTURE',
        ])
            expect(canRequestCancellation({...order(), status})).toBe(false);
        expect(canRequestCancellation({...order(), tradeEnv: 'LIVE'})).toBe(false);
    });
});
