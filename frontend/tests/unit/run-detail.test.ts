import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {runQueryKeys} from '../../src/api/query-keys';
import {runsApi} from '../../src/features/runs/api/runs';

const request = {limit: 1, offset: 0};
const order = {
    orderId: 'order',
    clientOrderId: 'client',
    exchangeOrderId: null,
    orderStatus: 'ACCEPTED',
    symbol: 'BTC-USDT',
    side: 'BUY',
    orderType: 'LIMIT',
    price: '9007199254740993.12345678',
    quantity: '0.00000001',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
};
const trade = {
    tradeId: 'trade',
    orderId: 'order',
    exchangeTradeId: 'venue-trade',
    exchangeOrderId: 'venue-order',
    symbol: 'BTC-USDT',
    price: order.price,
    quantity: order.quantity,
    fee: '-0.00000001',
    feeCurrency: 'BNB',
    tradeTs: order.createdAt,
};
afterEach(() => vi.restoreAllMocks());

describe('bounded canonical run detail', () => {
    it('uses exact encoded identities, bounded windows and abort signals', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {strategyRunId: 'run/1', items: [order], total: 1, ...request},
        });
        const signal = new AbortController().signal;
        expect((await runsApi.orders('run/1', request, signal)).items[0]).toEqual(order);
        expect(get).toHaveBeenCalledWith('/strategy-runs/run%2F1/orders/page', {
            params: request,
            signal,
        });
    });
    it('preserves signed, zero and null fees with their recorded currency', async () => {
        for (const fee of ['0.00000001', '0.00000000', '-0.00000001', null]) {
            const row = {...trade, fee};
            vi.spyOn(apiClient, 'get').mockResolvedValue({
                data: {strategyRunId: 'run', items: [row], total: 1, ...request},
            });
            expect((await runsApi.trades('run', request)).items[0]).toEqual(row);
        }
    });
    it('rejects wrong run, damaged windows and already-rounded numeric facts', async () => {
        for (const data of [
            {strategyRunId: 'other', items: [trade], total: 1, ...request},
            {strategyRunId: 'run', items: [], total: 527, ...request},
            {
                strategyRunId: 'run',
                items: [{...trade, price: 9007199254740994}],
                total: 1,
                ...request,
            },
            {strategyRunId: 'run', items: [{...trade, fee: -0.00000001}], total: 1, ...request},
        ]) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({data});
            await expect(runsApi.trades('run', request)).rejects.toThrow();
        }
    });
    it('does not replace a missing overview with another run or a false zero count', async () => {
        for (const data of [
            {
                run: {strategyRunId: 'other', strategyId: 'str'},
                orderCount: 0,
                tradeCount: 0,
                unavailableRelationships: [],
            },
            {
                run: {strategyRunId: 'run', strategyId: 'str'},
                orderCount: -1,
                tradeCount: 0,
                unavailableRelationships: [],
            },
        ]) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({data});
            await expect(runsApi.overview('run')).rejects.toThrow();
        }
    });
    it('isolates identities, owners, page kinds and cancellation identities in the cache', () => {
        const key = runQueryKeys.related(1, 'run', 'trades', request);
        for (const other of [
            runQueryKeys.related(2, 'run', 'trades', request),
            runQueryKeys.related(1, 'other', 'trades', request),
            runQueryKeys.related(1, null, 'trades', request),
            runQueryKeys.related(1, 'run', 'orders', request),
            runQueryKeys.related(1, 'run', 'trades', {...request, offset: 1}),
        ])
            expect(other).not.toEqual(key);
        expect(runQueryKeys.overview(1, 'run').slice(0, 4)).toEqual(key.slice(0, 4));
    });
});
