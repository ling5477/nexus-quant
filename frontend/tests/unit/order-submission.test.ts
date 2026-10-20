import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {accountsApi} from '@/features/accounts/api/accounts';
import {tradingWorkbenchApi} from '@/features/trading/api/trading-workbench';
import {
    confirmOriginalOrder,
    normalizeManualOrder,
    orderSubmission,
    readOrderSubmission,
    type PendingOrderSubmission,
} from '@/features/trading/order-submission';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {CurrentUser} from '@/types/auth';
import type {OrderView} from '@/features/trading/types/trading-workbench';
import {parseTradingWorkbenchResponse} from '@/features/trading/trading-response';

const data = new Map<string, string>();
const locks = new Set<string>();
const storageKey = 'nq:order-submission:v1:17';
const client = 'manual-22222222222242228222222222222222';
const user: CurrentUser = {
    userId: 17,
    username: 'ordinary-owner',
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
    accountAlias: 'Owner SIM',
    status: 'ACTIVE',
    externalAccountRef: null,
    isDefault: true,
};
const input = {
    symbol: 'BTC-USDT',
    side: 'BUY',
    orderType: 'LIMIT',
    price: '9007199254740993.12345678',
    quantity: '12345678.12345678',
};
const order = (): OrderView => ({
    orderId: 'ord-33333333-3333-4333-8333-333333333333',
    accountId: 101,
    venue: 'OKX',
    tradeEnv: 'SIM',
    symbol: 'BTC-USDT',
    clientOrderId: client,
    side: 'BUY',
    type: 'LIMIT',
    price: input.price,
    quantity: input.quantity,
    status: 'RISK_REJECTED',
    externalOrderId: null,
    traceId: 'trace',
    createdAt: '2026-10-16T00:00:00Z',
    updatedAt: '2026-10-16T00:00:00Z',
});
const pending = (): PendingOrderSubmission => {
    const value = readOrderSubmission(17);
    if (value.kind !== 'saved') throw new Error('expected original request');
    return value.pending;
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
    vi.stubGlobal('crypto', {randomUUID: () => '22222222-2222-4222-8222-222222222222'});
    vi.stubGlobal('navigator', {
        locks: {
            request: async (
                key: string,
                _options: unknown,
                callback: (lock: unknown) => Promise<unknown>,
            ) => {
                if (locks.has(key)) return callback(null);
                locks.add(key);
                try {
                    return await callback({key});
                } finally {
                    locks.delete(key);
                }
            },
        },
    });
    useAuthStore.setState({currentUser: {...user}, accessToken: 'session-a', tokenType: 'Bearer'});
    useAccountContextStore.getState().setSelectedAccount(account);
    vi.spyOn(accountsApi, 'detail').mockResolvedValue(account);
    vi.spyOn(tradingWorkbenchApi, 'placeOrder').mockResolvedValue({
        action: 'placeOrder',
        traceId: 'trace',
        detail: 'order_id=untrusted, status=FILLED',
    });
    vi.spyOn(tradingWorkbenchApi, 'getOrderByClientIdentity').mockImplementation(async () =>
        order(),
    );
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null, accessToken: null});
    useAccountContextStore.getState().clearAccountContext();
});
async function loseResponse() {
    vi.mocked(tradingWorkbenchApi.placeOrder).mockRejectedValueOnce({
        status: 0,
        code: 'NETWORK_ERROR',
    });
    await orderSubmission.submit(input).catch(() => undefined);
    return pending();
}

describe('普通人工委托原身份与未知结果恢复', () => {
    it.each([
        {quantity: '0.00000001', price: '1.00000000', rawQuantity: '1E-8', rawPrice: '1.00000000'},
        {
            quantity: '0.00000010',
            price: '1.00000000',
            rawQuantity: '1.0E-7',
            rawPrice: '1.00000000',
        },
        {quantity: '1.00000000', price: '0.00000001', rawQuantity: '1.00000000', rawPrice: '1E-8'},
        {quantity: '1', price: '100', rawQuantity: '1e+0', rawPrice: '1.00e+2'},
        {
            quantity: '0.00000001',
            price: '0.00000100',
            rawQuantity: '100000000E-16',
            rawPrice: '0.00000100',
        },
    ])('正式Jackson指数词法仍按精确原输入确认 $rawQuantity / $rawPrice', async (sample) => {
        const raw = JSON.stringify(order())
            .replace('"quantity":"' + input.quantity + '"', '"quantity":' + sample.rawQuantity)
            .replace('"price":"' + input.price + '"', '"price":' + sample.rawPrice);
        const response = parseTradingWorkbenchResponse(raw) as OrderView;
        vi.mocked(tradingWorkbenchApi.getOrderByClientIdentity).mockResolvedValueOnce(response);
        expect(
            (
                await orderSubmission.submit({
                    ...input,
                    quantity: sample.quantity,
                    price: sample.price,
                })
            ).orderId,
        ).toBe(order().orderId);
        expect(pending().phase).toBe('CONFIRMED');
        await orderSubmission.acknowledge(pending());
        expect(readOrderSubmission(17)).toEqual({kind: 'none'});
    });
    it.each(['1E-9', '1E+30', '1E+999', '1E-999', '-1E-8', '0E-8', 'NaN', '1E-8x'])(
        '无效或越界正式指数响应%s保持未知',
        async (value) => {
            const saved = await loseResponse();
            vi.mocked(tradingWorkbenchApi.getOrderByClientIdentity).mockResolvedValueOnce({
                ...order(),
                quantity: value,
            });
            await expect(orderSubmission.lookup(saved)).rejects.toMatchObject({
                code: 'ORDER_SUBMISSION_RESPONSE_INVALID',
            });
            expect(pending().phase).toBe('UNKNOWN');
        },
    );
    it('POST前已持久保存原账户和精确输入；回执不能伪装成交，正式GET确认Risk拒绝', async () => {
        vi.mocked(tradingWorkbenchApi.placeOrder).mockImplementationOnce(async (request) => {
            const saved = pending();
            expect(saved.request).toEqual(request);
            expect(saved.request.price).toBe(input.price);
            expect(saved.request.quantity).toBe(input.quantity);
            expect(saved.request.clientOrderId).toBe(client);
            expect(saved.legacyAccountId).toBe(101);
            expect(saved.request.accountId).toBe(501);
            expect(request.strategyRunId).toBeUndefined();
            return {action: 'placeOrder', traceId: 'untrusted', detail: 'status=FILLED'};
        });
        const result = await orderSubmission.submit(input);
        expect(result.status).toBe('RISK_REJECTED');
        expect(pending().phase).toBe('CONFIRMED');
        expect(tradingWorkbenchApi.getOrderByClientIdentity).toHaveBeenCalledWith(501, client);
    });
    it('响应丢失和重新读取仅保留UNKNOWN，不隐式GET/POST，也不允许换号', async () => {
        await loseResponse();
        expect(pending().phase).toBe('UNKNOWN');
        expect(readOrderSubmission(17).kind).toBe('saved');
        await expect(orderSubmission.submit({...input, quantity: '1'})).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_PENDING',
        });
        expect(tradingWorkbenchApi.placeOrder).toHaveBeenCalledTimes(1);
        expect(tradingWorkbenchApi.getOrderByClientIdentity).not.toHaveBeenCalled();
    });
    it('查询404保留未知，显式继续发送相同冻结身份和参数', async () => {
        const saved = await loseResponse();
        await expect(orderSubmission.resume(saved)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_LOOKUP_REQUIRED',
        });
        vi.mocked(tradingWorkbenchApi.getOrderByClientIdentity).mockRejectedValueOnce({
            status: 404,
        });
        await expect(orderSubmission.lookup(saved)).rejects.toMatchObject({status: 404});
        expect(pending().phase).toBe('UNKNOWN');
        expect(tradingWorkbenchApi.placeOrder).toHaveBeenCalledTimes(1);
        const result = await orderSubmission.resume(saved);
        expect(result.orderId).toBe(order().orderId);
        const calls = vi.mocked(tradingWorkbenchApi.placeOrder).mock.calls;
        expect(calls[1][0]).toEqual(calls[0][0]);
        expect(pending().phase).toBe('CONFIRMED');
    });
    it('500查询错误不能授权继续；确认原订单后不能再次发送', async () => {
        const saved = await loseResponse();
        vi.mocked(tradingWorkbenchApi.getOrderByClientIdentity).mockRejectedValueOnce({
            status: 500,
        });
        await orderSubmission.lookup(saved).catch(() => undefined);
        await expect(orderSubmission.resume(saved)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_LOOKUP_REQUIRED',
        });
        await orderSubmission.lookup(saved);
        await expect(orderSubmission.resume(pending())).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_LOOKUP_REQUIRED',
        });
        expect(tradingWorkbenchApi.placeOrder).toHaveBeenCalledTimes(1);
    });
    it('未知记录不可清理；确认后明确清理才允许下一笔', async () => {
        const saved = await loseResponse();
        await expect(orderSubmission.acknowledge(saved)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_LOOKUP_REQUIRED',
        });
        await orderSubmission.lookup(saved);
        await orderSubmission.acknowledge(pending());
        expect(readOrderSubmission(17)).toEqual({kind: 'none'});
    });
    it('存储失败和读回不一致都发生在POST之前', async () => {
        vi.spyOn(localStorage, 'setItem').mockImplementationOnce(() => {
            throw new Error('quota');
        });
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_STORAGE_UNAVAILABLE',
        });
        vi.spyOn(localStorage, 'setItem').mockImplementation(() => undefined);
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_STORAGE_UNAVAILABLE',
        });
        expect(tradingWorkbenchApi.placeOrder).not.toHaveBeenCalled();
    });
    it('无法跨标签页协调时不生成或发送委托', async () => {
        vi.stubGlobal('navigator', {});
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_COORDINATION_UNAVAILABLE',
        });
        expect(data.size).toBe(0);
        expect(tradingWorkbenchApi.placeOrder).not.toHaveBeenCalled();
    });
    it('同一主体并发提交由独占锁阻止第二笔', async () => {
        let release!: (value: typeof account) => void;
        vi.mocked(accountsApi.detail).mockReturnValueOnce(
            new Promise((resolve) => {
                release = resolve;
            }),
        );
        const first = orderSubmission.submit(input);
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_PENDING',
        });
        release(account);
        await first;
        expect(tradingWorkbenchApi.placeOrder).toHaveBeenCalledTimes(1);
    });
    it.each(['LIVE', 'DEMO', 'UNKNOWN'])('非SIM环境%s不能发送', async (environment) => {
        useAccountContextStore.setState({tradeEnv: environment});
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_ACCOUNT_UNAVAILABLE',
        });
        expect(tradingWorkbenchApi.placeOrder).not.toHaveBeenCalled();
    });
    it('正式账户停用或桥接变化不发送订单', async () => {
        vi.mocked(accountsApi.detail).mockResolvedValueOnce({...account, legacyAccountId: 999});
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_ACCOUNT_UNAVAILABLE',
        });
        expect(tradingWorkbenchApi.placeOrder).not.toHaveBeenCalled();
        expect(pending().phase).toBe('UNKNOWN');
    });
    it('切账户后原请求不可继续；原记录不迁移到新账户', async () => {
        const saved = await loseResponse();
        useAccountContextStore
            .getState()
            .setSelectedAccount({...account, exchangeAccountId: 502, legacyAccountId: 102});
        await expect(orderSubmission.lookup(saved)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_SCOPE_CHANGED',
        });
        expect(pending().request.accountId).toBe(501);
    });
    it('POST等待期间更换主体时，迟到响应不能确认原单或污染新主体记录', async () => {
        let release!: (value: {action: string; traceId: string; detail: string}) => void;
        vi.mocked(tradingWorkbenchApi.placeOrder).mockReturnValueOnce(
            new Promise((resolve) => {
                release = resolve;
            }),
        );
        const result = orderSubmission.submit(input);
        await vi.waitFor(() => expect(tradingWorkbenchApi.placeOrder).toHaveBeenCalledTimes(1));
        useAuthStore.setState({currentUser: {...user, userId: 18}, accessToken: 'session-b'});
        release({action: 'placeOrder', traceId: 'late', detail: 'status=FILLED'});
        await expect(result).rejects.toMatchObject({code: 'ORDER_SUBMISSION_SCOPE_CHANGED'});
        expect(readOrderSubmission(18)).toEqual({kind: 'none'});
        expect(pending().phase).toBe('UNKNOWN');
        expect(tradingWorkbenchApi.getOrderByClientIdentity).not.toHaveBeenCalled();
    });
    it('同用户重新登录后的旧响应也不可确认；新会话须重新查询才能继续', async () => {
        const saved = await loseResponse();
        vi.mocked(tradingWorkbenchApi.getOrderByClientIdentity).mockRejectedValueOnce({
            status: 404,
        });
        await orderSubmission.lookup(saved).catch(() => undefined);
        useAuthStore.setState({accessToken: 'session-a-new'});
        await expect(orderSubmission.resume(saved)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_LOOKUP_REQUIRED',
        });
        expect(tradingWorkbenchApi.placeOrder).toHaveBeenCalledTimes(1);
    });
    it('同用户新会话不能接收上一会话迟到的正式GET结果', async () => {
        const saved = await loseResponse();
        let release!: (value: OrderView) => void;
        vi.mocked(tradingWorkbenchApi.getOrderByClientIdentity).mockReturnValueOnce(
            new Promise((resolve) => {
                release = resolve;
            }),
        );
        const result = orderSubmission.lookup(saved);
        await vi.waitFor(() =>
            expect(tradingWorkbenchApi.getOrderByClientIdentity).toHaveBeenCalledTimes(1),
        );
        useAuthStore.setState({accessToken: 'relogged-same-owner'});
        release(order());
        await expect(result).rejects.toMatchObject({code: 'ORDER_SUBMISSION_SCOPE_CHANGED'});
        expect(pending().phase).toBe('UNKNOWN');
    });
    it('查询404到达前切换账户，不会授予原编号继续资格', async () => {
        const saved = await loseResponse();
        let reject!: (reason: unknown) => void;
        vi.mocked(tradingWorkbenchApi.getOrderByClientIdentity).mockReturnValueOnce(
            new Promise((_resolve, rejectValue) => {
                reject = rejectValue;
            }),
        );
        const result = orderSubmission.lookup(saved);
        await vi.waitFor(() =>
            expect(tradingWorkbenchApi.getOrderByClientIdentity).toHaveBeenCalledTimes(1),
        );
        useAccountContextStore
            .getState()
            .setSelectedAccount({...account, exchangeAccountId: 502, legacyAccountId: 102});
        reject({status: 404});
        await expect(result).rejects.toMatchObject({code: 'ORDER_SUBMISSION_SCOPE_CHANGED'});
        useAccountContextStore.getState().setSelectedAccount(account);
        await expect(orderSubmission.resume(saved)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_LOOKUP_REQUIRED',
        });
    });
    it('VIEWER可查询本人原单但不能提交或继续', async () => {
        const saved = await loseResponse();
        useAuthStore.setState({currentUser: {...user, roles: ['VIEWER']}});
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_SCOPE_UNAVAILABLE',
        });
        await expect(orderSubmission.resume(saved)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_SCOPE_UNAVAILABLE',
        });
        expect((await orderSubmission.lookup(saved)).orderId).toBe(order().orderId);
    });
    it('存储记录被修改后不可恢复，不能使用当前编辑输入替换原值', async () => {
        const saved = await loseResponse();
        const tampered = {...saved, request: {...saved.request, quantity: '1'}};
        await expect(orderSubmission.resume(tampered)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_STORAGE_UNAVAILABLE',
        });
        data.set(storageKey, '{"request":');
        expect(readOrderSubmission(17)).toEqual({kind: 'unavailable'});
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_PENDING',
        });
    });
    it.each([
        {accountId: 999},
        {clientOrderId: 'wrong'},
        {tradeEnv: 'LIVE'},
        {venue: 'BINANCE'},
        {symbol: 'ETH-USDT'},
        {side: 'SELL'},
        {quantity: '12345678.12345679'},
        {price: '9007199254740993.12345679'},
        {status: 'INVENTED'},
    ])('正式查询范围或经济输入不匹配%s时保持未知', async (change) => {
        const saved = await loseResponse();
        vi.mocked(tradingWorkbenchApi.getOrderByClientIdentity).mockResolvedValueOnce({
            ...order(),
            ...change,
        });
        await expect(orderSubmission.lookup(saved)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_RESPONSE_INVALID',
        });
        expect(pending().phase).toBe('UNKNOWN');
    });
    it('数据库补齐尾零不改变精确数值；一位有效数字变化仍拒绝', async () => {
        const saved = await loseResponse();
        const request = {...saved, request: {...saved.request, quantity: '1', price: '100'}};
        expect(
            confirmOriginalOrder(request, {
                ...order(),
                quantity: '1.00000000',
                price: '100.00000000',
            }).orderId,
        ).toBe(order().orderId);
    });
    it.each(['0', '-1', '1e10', '0.000000001', '1.123456789', '9'.repeat(31), 'NaN'])(
        '非法或超出numeric(38,8)输入%s拒绝发送',
        async (value) => {
            await expect(orderSubmission.submit({...input, quantity: value})).rejects.toMatchObject(
                {code: 'ORDER_SUBMISSION_AMOUNT_INVALID'},
            );
            expect(tradingWorkbenchApi.placeOrder).not.toHaveBeenCalled();
        },
    );
    it('市价委托省略遗留价格，避免把隐藏表单字段写入冻结请求', () => {
        expect(normalizeManualOrder({...input, orderType: 'MARKET'})).toEqual({
            symbol: 'BTC-USDT',
            side: 'BUY',
            orderType: 'MARKET',
            quantity: input.quantity,
        });
    });
    it('POST返回400也不能无依据清理可能已发生的写入', async () => {
        vi.mocked(tradingWorkbenchApi.placeOrder).mockRejectedValueOnce({status: 400});
        await orderSubmission.submit(input).catch(() => undefined);
        expect(pending().phase).toBe('UNKNOWN');
        await expect(orderSubmission.submit(input)).rejects.toMatchObject({
            code: 'ORDER_SUBMISSION_PENDING',
        });
    });
});
