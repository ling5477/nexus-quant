import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {runQueryKeys} from '../../src/api/query-keys';
import {runsApi} from '../../src/features/runs/api/runs';
import {defaultStrategyRunListFilters} from '../../src/features/runs/types/runs';

const request = {...defaultStrategyRunListFilters, strategyId: 'str', limit: 1, offset: 0};
const row = {
    strategyRunId: 'run',
    strategyId: 'str',
    scheduleJobId: 'sch',
    status: 'FAILED',
    triggerType: 'RECOVERY',
    startedAt: '2026-10-01T00:00:00Z',
};
afterEach(() => vi.restoreAllMocks());

describe('scoped run history paging', () => {
    it('queries the whole matching history through a bounded cancellable endpoint', async () => {
        const data = {items: [row], total: 527, limit: 1, offset: 0};
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data});
        const signal = new AbortController().signal;
        const filters = {...request, status: 'FAILED', triggerType: 'RECOVERY'};
        expect(await runsApi.page(filters, signal)).toEqual(data);
        expect(get).toHaveBeenCalledWith('/strategy-runs/page', {params: filters, signal});
    });

    it('refuses wrong scope or filters and damaged pages rather than showing false history', async () => {
        for (const data of [
            {items: [row], total: 1, limit: 2, offset: 0},
            {items: [], total: 527, limit: 1, offset: 0},
            {items: [{...row, strategyId: 'other'}], total: 1, limit: 1, offset: 0},
            {items: [{...row, status: 'STOPPED'}], total: 1, limit: 1, offset: 0},
            {items: [{...row, triggerType: 'FAKE'}], total: 1, limit: 1, offset: 0},
        ]) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({data});
            await expect(runsApi.page(request)).rejects.toThrow();
        }
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {items: [row], total: 1, limit: 1, offset: 0},
        });
        await expect(
            runsApi.page({...request, strategyId: '', scheduleId: 'other'}),
        ).rejects.toThrow();
        await expect(runsApi.page({...request, status: 'RUNNING'})).rejects.toThrow();
        await expect(runsApi.page({...request, triggerType: 'MANUAL'})).rejects.toThrow();
    });

    it('accepts all actual triggers and a real empty tail', async () => {
        for (const triggerType of ['MANUAL', 'SCHEDULED', 'SCHEDULER', 'RECOVERY']) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({
                data: {items: [{...row, triggerType}], total: 1, limit: 1, offset: 0},
            });
            expect((await runsApi.page(request)).items[0].triggerType).toBe(triggerType);
        }
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {items: [], total: 527, limit: 1, offset: 600},
        });
        expect((await runsApi.page({...request, offset: 600})).items).toEqual([]);
    });

    it('separates the owner, scope, every filter, page and refresh in cache identity', () => {
        const original = runQueryKeys.page(1, request, 1);
        for (const delta of [
            {strategyId: 'other'},
            {scheduleId: 'sch'},
            {status: 'FAILED'},
            {triggerType: 'RECOVERY'},
            {limit: 2},
            {offset: 1},
        ]) {
            expect(runQueryKeys.page(1, {...request, ...delta}, 1)).not.toEqual(original);
        }
        expect(runQueryKeys.page(2, request, 1)).not.toEqual(original);
        expect(runQueryKeys.page(1, request, 2)).not.toEqual(original);
        expect(runQueryKeys.overview(1, 'run')).not.toEqual(runQueryKeys.overview(2, 'run'));
        expect(runQueryKeys.overview(1, 'run')).not.toEqual(runQueryKeys.overview(1, null));
    });

    it('encodes an exact overview identity and forwards cancellation', async () => {
        const data = {
            run: {...row, strategyRunId: 'run/1'},
            orderCount: 0,
            tradeCount: 0,
            unavailableRelationships: [],
        };
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data});
        const signal = new AbortController().signal;
        await runsApi.overview('run/1', signal);
        expect(get).toHaveBeenCalledWith('/strategy-runs/run%2F1/overview', {signal});
    });
});
