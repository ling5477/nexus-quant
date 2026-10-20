import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {strategyQueryKeys} from '../../src/api/query-keys';
import {strategiesApi} from '../../src/features/strategies/api/strategies';

const request = {limit: 1, offset: 0};
const row = {
    strategyVersionId: 'v/1',
    strategyCode: 'parent code',
    version: 1,
    versionName: 'version',
    status: 'DRAFT',
    checksum: 'digest',
    createdBy: 'user',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
};
afterEach(() => vi.restoreAllMocks());

describe('parent-scoped strategy version paging', () => {
    it('uses the bounded encoded endpoint with cancellation and all explicit filters', async () => {
        const filters = {
            ...request,
            versionName: '%_!',
            strategyVersionId: 'v/1',
            version: 1,
            status: 'DRAFT',
        };
        const data = {items: [row], total: 527, ...request};
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data});
        const signal = new AbortController().signal;
        expect(await strategiesApi.pageVersions('parent code', filters, signal)).toEqual(data);
        expect(get).toHaveBeenCalledWith('/strategies/parent%20code/versions/page', {
            params: filters,
            signal,
        });
    });

    it('rejects wrong parent, exact identity, invalid lifecycle, damaged windows and duplicate identities', async () => {
        for (const data of [
            {items: [{...row, strategyCode: 'other'}], total: 1, ...request},
            {items: [{...row, version: 0}], total: 1, ...request},
            {items: [{...row, status: 'RUNNING'}], total: 1, ...request},
            {items: [], total: 1, ...request},
            {items: [row], total: 1, limit: 2, offset: 0},
        ]) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({data});
            await expect(strategiesApi.pageVersions('parent code', request)).rejects.toThrow();
        }
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: {items: [row], total: 1, ...request}});
        await expect(
            strategiesApi.pageVersions('parent code', {...request, strategyVersionId: 'other'}),
        ).rejects.toThrow();
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {items: [row, row], total: 2, limit: 2, offset: 0},
        });
        await expect(
            strategiesApi.pageVersions('parent code', {...request, limit: 2}),
        ).rejects.toThrow();
    });

    it('reads one exact snapshot and preserves precision as original text', async () => {
        const raw = '{"number":9007199254740993.123456789012345678}';
        const data = {
            ...row,
            paramSnapshotJson: raw,
            configSnapshotJson: '{}',
            sourceSnapshotJson: '{}',
        };
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data});
        const signal = new AbortController().signal;
        expect(
            (await strategiesApi.versionDetail('parent code', 'v/1', signal)).paramSnapshotJson,
        ).toBe(raw);
        expect(get).toHaveBeenCalledWith('/strategies/parent%20code/versions/v%2F1', {signal});
        get.mockResolvedValue({data: {...data, strategyCode: 'other'}});
        await expect(strategiesApi.versionDetail('parent code', 'v/1')).rejects.toThrow();
        get.mockResolvedValue({data: {...data, paramSnapshotJson: {number: 1}}});
        await expect(strategiesApi.versionDetail('parent code', 'v/1')).rejects.toThrow();
    });

    it('isolates owner, parent, filters, window and closed observer identity while retaining mutation prefix', () => {
        const original = strategyQueryKeys.versionPage(1, 'p', request, 0);
        for (const patch of [
            {strategyVersionId: 'v'},
            {versionName: 'n'},
            {version: 1},
            {status: 'ARCHIVED'},
            {limit: 20},
            {offset: 20},
        ])
            expect(strategyQueryKeys.versionPage(1, 'p', {...request, ...patch}, 0)).not.toEqual(
                original,
            );
        expect(strategyQueryKeys.versionPage(2, 'p', request, 0)).not.toEqual(original);
        expect(strategyQueryKeys.versionPage(1, 'other', request, 0)).not.toEqual(original);
        expect(strategyQueryKeys.versionPage(1, 'p', request, -1)).not.toEqual(original);
        expect(original.slice(0, 3)).toEqual(strategyQueryKeys.versions('p'));
        expect(strategyQueryKeys.scopedVersionDetail(1, 'p', 'v')).not.toEqual(
            strategyQueryKeys.scopedVersionDetail(2, 'p', 'v'),
        );
        expect(strategyQueryKeys.scopedVersionDetail(1, 'p', 'v')).not.toEqual(
            strategyQueryKeys.scopedVersionDetail(1, null, null),
        );
    });
});
