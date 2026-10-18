import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {scheduleQueryKeys} from '../../src/api/query-keys';
import {schedulesApi} from '../../src/features/schedules/api/schedules';

const request = {
    strategyId: 'str',
    scheduleId: '',
    status: '',
    scheduleType: '',
    limit: 1,
    offset: 0,
};
const item = {
    scheduleJobId: 'sch/1',
    strategyId: 'str',
    scheduleType: 'CRON',
    cronExpr: '0 * * * * *',
    timezone: 'UTC',
    enabled: true,
    status: 'ENABLED',
    accountId: '9007199254740993',
    exchangeCode: 'BINANCE',
    tradeEnv: 'SIM',
    lastTriggeredAt: null,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
};
afterEach(() => vi.restoreAllMocks());

describe('bounded strategy schedule reads', () => {
    it('uses a server page and passes cancellation without loading the old array', async () => {
        const signal = new AbortController().signal;
        const get = vi
            .spyOn(apiClient, 'get')
            .mockResolvedValue({data: {items: [item], total: 1, limit: 1, offset: 0}});
        expect((await schedulesApi.page(request, signal)).items[0]).toEqual(item);
        expect(get).toHaveBeenCalledWith('/strategy-schedules/page', {params: request, signal});
    });
    it('rejects truncated, repeated, cross-strategy and mismatched state rows', async () => {
        for (const change of [
            {items: []},
            {items: [{...item, strategyId: 'other'}]},
            {items: [{...item, status: 'DISABLED'}]},
            {items: [{...item, accountId: 9007199254740994}]},
            {offset: 1},
        ]) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({
                data: {items: [item], total: 1, limit: 1, offset: 0, ...change},
            });
            await expect(schedulesApi.page(request)).rejects.toThrow();
        }
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {items: [item, item], total: 2, limit: 2, offset: 0},
        });
        await expect(schedulesApi.page({...request, limit: 2})).rejects.toThrow();
    });
    it('keeps legacy type filters in the real request and rejects cross-type rows', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {
                items: [{...item, scheduleType: 'MANUAL'}],
                total: 1,
                limit: 1,
                offset: 0,
            },
        });
        await schedulesApi.page({...request, scheduleType: 'MANUAL'});
        expect(get.mock.calls[0][1]?.params.scheduleType).toBe('MANUAL');
        await expect(schedulesApi.page({...request, scheduleType: 'INTERVAL'})).rejects.toThrow();
    });
    it('encodes exact details and retains the legacy integer account before JSON parsing', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {...item, windowConfig: '{}', dedupScope: 'SCHEDULE_WINDOW'},
        });
        const signal = new AbortController().signal;
        await schedulesApi.detail('sch/1', signal);
        expect(get.mock.calls[0][0]).toBe('/strategy-schedules/sch%2F1');
        const config = get.mock.calls[0][1] as unknown as {
            transformResponse: Array<(raw: string) => unknown>;
            signal: AbortSignal;
        };
        expect(config.signal).toBe(signal);
        expect(config.transformResponse[0]('{"accountId":9007199254740993}')).toEqual({
            accountId: '9007199254740993',
        });
    });
    it('uses the formal desired-state mutation and rejects a mismatched receipt', async () => {
        const patch = vi
            .spyOn(apiClient, 'patch')
            .mockResolvedValue({data: {...item, enabled: false, status: 'DISABLED'}});
        await schedulesApi.updateStatus('sch/1', {enabled: false});
        expect(patch.mock.calls[0][0]).toBe('/strategy-schedules/sch%2F1/status');
        expect(patch.mock.calls[0][1]).toEqual({enabled: false});
        patch.mockResolvedValue({data: item});
        await expect(schedulesApi.updateStatus('sch/1', {enabled: false})).rejects.toThrow();
    });
    it('separates owners, filters, page windows and exact detail identities', () => {
        const key = scheduleQueryKeys.page(1, request, 1);
        for (const other of [
            scheduleQueryKeys.page(2, request, 1),
            scheduleQueryKeys.page(1, {...request, offset: 1}, 1),
            scheduleQueryKeys.page(1, {...request, status: 'DISABLED'}, 1),
            scheduleQueryKeys.page(1, {...request, scheduleType: 'MANUAL'}, 1),
            scheduleQueryKeys.page(1, {...request, scheduleId: 'sch'}, 1),
        ])
            expect(other).not.toEqual(key);
        expect(scheduleQueryKeys.detail(1, 'sch')).not.toEqual(scheduleQueryKeys.detail(2, 'sch'));
    });
});
