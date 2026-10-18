import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {backtestsApi, validateBacktestPage} from '../../src/features/backtests/backtestsApi';
import {backtestsQueryKeys} from '../../src/api/query-keys';
import type {
    BacktestPageRequest,
    BacktestConfigListItem,
} from '../../src/features/backtests/backtestsTypes';

const request: BacktestPageRequest = {
    researchConfigId: 'rcf-parent',
    backtestConfigId: '',
    name: '',
    limit: 10,
    offset: 10,
};
const rows = Array.from(
    {length: 10},
    (_, index) =>
        ({
            backtestConfigId: `bcf-${index}`,
            researchConfigId: 'rcf-parent',
            name: `name-${index}`,
            createdAt: '2026-10-15T00:00:00Z',
        }) as BacktestConfigListItem,
);
afterEach(() => vi.restoreAllMocks());

describe('backtest configuration server paging', () => {
    it('uses the formal GET window and cancellation while retaining exact monetary JSON parsing', async () => {
        const response = {items: rows, total: 521, limit: 10, offset: 10};
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: response});
        const signal = new AbortController().signal;
        expect(await backtestsApi.page(request, signal)).toEqual(response);
        expect(get.mock.calls[0][0]).toBe('/backtest-configs/page');
        const config = get.mock.calls[0][1]!;
        expect(config.params).toEqual(request);
        expect(config.signal).toBe(signal);
        const transform = config.transformResponse as ((text: string) => unknown)[];
        const parsed = transform[0](
            '{"items":[{"initialCapital":9007199254740993.123456789012345678}]}',
        ) as {items: {initialCapital: string}[]};
        expect(parsed.items[0].initialCapital).toBe('9007199254740993.123456789012345678');
    });

    it('rejects wrong windows, truncated pages, duplicate identities and unsafe totals', () => {
        for (const value of [
            null,
            {items: rows, total: 521, limit: 10, offset: 0},
            {items: rows, total: Number.MAX_SAFE_INTEGER + 1, limit: 10, offset: 10},
            {items: rows.slice(0, 1), total: 521, limit: 10, offset: 10},
            {items: [...rows.slice(0, 9), rows[0]], total: 521, limit: 10, offset: 10},
            {
                items: rows.map((row, index) => (index ? row : {...row, researchConfigId: ''})),
                total: 521,
                limit: 10,
                offset: 10,
            },
        ])
            expect(() => validateBacktestPage(value, request)).toThrow();
        expect(
            validateBacktestPage(
                {items: [], total: 521, limit: 10, offset: 530},
                {...request, offset: 530},
            ).items,
        ).toEqual([]);
    });

    it('isolates every filter, window and authenticated owner in the query identity', () => {
        const original = backtestsQueryKeys.page(1, request, 1);
        for (const candidate of [
            backtestsQueryKeys.page(2, request, 1),
            backtestsQueryKeys.page(1, {...request, researchConfigId: 'other'}, 1),
            backtestsQueryKeys.page(1, {...request, backtestConfigId: 'bcf-other'}, 1),
            backtestsQueryKeys.page(1, {...request, name: 'changed'}, 1),
            backtestsQueryKeys.page(1, {...request, limit: 20}, 1),
            backtestsQueryKeys.page(1, {...request, offset: 20}, 1),
            backtestsQueryKeys.page(1, request, 2),
        ])
            expect(candidate).not.toEqual(original);
    });
});
