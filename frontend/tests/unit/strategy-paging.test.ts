import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {strategyQueryKeys} from '../../src/api/query-keys';
import {strategiesApi} from '../../src/features/strategies/api/strategies';
import {
    defaultStrategyListFilters,
    type StrategyPageRequest,
} from '../../src/features/strategies/types/strategies';

const request: StrategyPageRequest = {...defaultStrategyListFilters, limit: 1, offset: 0};
const row = {
    strategyId: 'str-1',
    strategyCode: 'code-1',
    strategyName: 'Name',
    enabled: false,
    createdAt: '2026-10-01T00:00:00Z',
};
afterEach(() => vi.restoreAllMocks());

describe('strategy server paging', () => {
    it('uses one bounded formal GET with cancellation and omits the all-enabled sentinel', async () => {
        const data = {items: [row], total: 500, limit: 1, offset: 0};
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data});
        const post = vi.spyOn(apiClient, 'post');
        const signal = new AbortController().signal;
        expect(await strategiesApi.page(request, signal)).toEqual(data);
        expect(get).toHaveBeenCalledWith('/strategies/page', {
            params: {...request, enabled: undefined},
            signal,
        });
        expect(post).not.toHaveBeenCalled();
    });

    it('preserves explicit disabled filtering and accepts a real empty tail', async () => {
        const get = vi
            .spyOn(apiClient, 'get')
            .mockResolvedValue({data: {items: [], total: 100, limit: 1, offset: 100}});
        expect(
            (await strategiesApi.page({...request, enabled: 'false', offset: 100})).items,
        ).toEqual([]);
        expect(get).toHaveBeenCalledWith('/strategies/page', {
            params: {...request, enabled: 'false', offset: 100},
            signal: undefined,
        });
    });

    it('rejects damaged windows, incomplete identities and the wrong exact strategy', async () => {
        for (const data of [
            {items: [row], total: 500, limit: 2, offset: 0},
            {items: [], total: 500, limit: 1, offset: 0},
            {items: [row], total: Number.MAX_SAFE_INTEGER + 1, limit: 1, offset: 0},
            {items: [{...row, strategyCode: ''}], total: 1, limit: 1, offset: 0},
            {items: [{...row, enabled: 'false'}], total: 1, limit: 1, offset: 0},
        ]) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({data});
            await expect(strategiesApi.page(request)).rejects.toThrow();
        }
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {items: [row], total: 1, limit: 1, offset: 0},
        });
        await expect(strategiesApi.page({...request, strategyId: 'str-other'})).rejects.toThrow();
    });

    it('distinguishes every filter, identity, owner and window in the cache', () => {
        const original = strategyQueryKeys.page(1, request, 1);
        for (const patch of [
            {strategyId: 'other'},
            {strategyCode: 'other'},
            {strategyName: 'other'},
            {strategyType: 'other'},
            {exchangeCode: 'other'},
            {tradeEnv: 'other'},
            {enabled: 'false' as const},
            {limit: 20},
            {offset: 20},
        ])
            expect(strategyQueryKeys.page(1, {...request, ...patch}, 1)).not.toEqual(original);
        expect(strategyQueryKeys.page(2, request, 1)).not.toEqual(original);
        expect(strategyQueryKeys.page(1, request, 2)).not.toEqual(original);
    });
});
