import {afterEach, describe, expect, it, vi} from 'vitest';
import {researchApi, validateResearchPage} from '../../src/features/research/api/research';
import {apiClient} from '../../src/api/client';
import {researchQueryKeys} from '../../src/api/query-keys';
import type {
    ResearchConfigListItem,
    ResearchPageRequest,
} from '../../src/features/research/types/research';

const request: ResearchPageRequest = {
    sourceStrategyId: 'source',
    researchConfigId: '',
    name: '',
    limit: 10,
    offset: 10,
};
const rows = Array.from(
    {length: 10},
    (_, index) =>
        ({
            researchConfigId: `rcf-${index}`,
            sourceStrategyId: 'source',
            name: `name-${index}`,
            createdAt: '2026-10-15T00:00:00Z',
        }) as ResearchConfigListItem,
);
afterEach(() => vi.restoreAllMocks());

describe('research server paging contract', () => {
    it('reads one exact GET window and passes cancellation without a business write', async () => {
        const response = {items: rows, total: 51, limit: 10, offset: 10};
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: response});
        const post = vi.spyOn(apiClient, 'post');
        const signal = new AbortController().signal;
        expect(await researchApi.page(request, signal)).toEqual(response);
        expect(get).toHaveBeenCalledWith('/research-configs/page', {params: request, signal});
        expect(post).not.toHaveBeenCalled();
    });

    it('accepts the actual total independently of the current page and accepts an empty tail', () => {
        expect(
            validateResearchPage({items: rows, total: 501, limit: 10, offset: 10}, request).total,
        ).toBe(501);
        expect(
            validateResearchPage(
                {items: [], total: 501, limit: 10, offset: 510},
                {...request, offset: 510},
            ).items,
        ).toEqual([]);
    });

    it('rejects wrong windows, incomplete pages, duplicate identities and unsafe totals', () => {
        for (const response of [
            null,
            {items: rows, total: -1, limit: 10, offset: 10},
            {items: rows, total: 501, limit: 20, offset: 10},
            {items: rows, total: 501, limit: 10, offset: 0},
            {items: rows.slice(0, 1), total: 501, limit: 10, offset: 10},
            {items: [...rows.slice(0, 9), rows[0]], total: 501, limit: 10, offset: 10},
            {items: rows, total: Number.MAX_SAFE_INTEGER + 1, limit: 10, offset: 10},
            {
                items: rows.map((row, index) => (index ? row : {...row, createdAt: 'invalid'})),
                total: 501,
                limit: 10,
                offset: 10,
            },
        ]) {
            expect(() => validateResearchPage(response, request)).toThrow();
        }
    });

    it('includes owner, every submitted filter and page window in the cache identity', () => {
        const original = researchQueryKeys.page(1, request, 1);
        for (const changed of [
            researchQueryKeys.page(2, request, 1),
            researchQueryKeys.page(1, {...request, name: 'new'}, 1),
            researchQueryKeys.page(1, {...request, sourceStrategyId: 'other'}, 1),
            researchQueryKeys.page(1, {...request, researchConfigId: 'rcf-2'}, 1),
            researchQueryKeys.page(1, {...request, offset: 20}, 1),
            researchQueryKeys.page(1, {...request, limit: 20}, 1),
            researchQueryKeys.page(1, request, 2),
        ])
            expect(changed).not.toEqual(original);
    });
});
