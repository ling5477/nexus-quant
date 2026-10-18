import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import type {CurrentUser} from '../../src/types/auth';
import type {
    CreateMarketdataIngestionJobRequest,
    MarketdataIngestionJob,
    MarketdataIngestionRun,
} from '../../src/types/marketdata';
import {
    confirmIngestion,
    ingestionOperations,
    ingestionReads,
    ingestionSymbols,
    normalizeIngestionInput,
    readIngestion,
    type PendingIngestion,
} from '../../src/features/marketdata/ingestion-operations';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

const stored = new Map<string, string>();
const held = new Set<string>();
const key = 'nq:ingestion-operation:v1:17';
const id = '22222222-2222-4222-8222-222222222222';
const otherId = '33333333-3333-4333-8333-333333333333';
const user: CurrentUser = {
    userId: 17,
    username: 'ingestion-owner',
    roles: ['OPERATOR'],
    authenticated: true,
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const input: CreateMarketdataIngestionJobRequest = {
    exchangeCode: 'OKX',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    interval: '1m',
    startTime: '2026-10-01T00:00:00.000Z',
    endTime: '2026-10-01T00:03:00.000Z',
};
const job = (): MarketdataIngestionJob => ({
    jobId: id,
    ...input,
    status: 'CREATED',
    source: 'EXCHANGE_HISTORICAL',
    createdBy: user.username,
    createdAt: '2026-10-16T00:00:00.123456Z',
    updatedAt: '2026-10-16T00:00:00.123456Z',
});
const run = (): MarketdataIngestionRun => ({
    jobId: otherId,
    runId: id,
    status: 'SUCCEEDED',
    fetchedBars: 1,
    insertedBars: 1,
    updatedBars: 0,
    skippedBars: 0,
    startedAt: '2026-10-16T00:00:00.123456Z',
    finishedAt: '2026-10-16T00:00:01.123456Z',
    requestedStartTime: input.startTime,
    requestedEndTime: input.endTime,
    actualStartTime: input.startTime,
    actualEndTime: '2026-10-01T00:00:59.999Z',
    errorMessage: null,
});

beforeEach(() => {
    stored.clear();
    held.clear();
    vi.stubGlobal('window', {dispatchEvent: vi.fn()});
    vi.stubGlobal('crypto', {randomUUID: () => id});
    vi.stubGlobal('localStorage', {
        getItem: (name: string) => stored.get(name) ?? null,
        setItem: (name: string, value: string) => stored.set(name, value),
        removeItem: (name: string) => stored.delete(name),
    });
    vi.stubGlobal('navigator', {
        locks: {
            request: async (
                name: string,
                _options: unknown,
                callback: (lock: unknown) => Promise<unknown>,
            ) => {
                if (held.has(name)) return callback(null);
                held.add(name);
                try {
                    return await callback({name});
                } finally {
                    held.delete(name);
                }
            },
        },
    });
    useAuthStore.setState({currentUser: {...user}, accessToken: 'synthetic-unit-token'});
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null, accessToken: null});
});
function pending(): PendingIngestion {
    const value = readIngestion(17);
    if (value.kind !== 'saved') throw new Error('original request must be saved');
    return value.pending;
}
async function lostCreate() {
    vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
    await expect(ingestionOperations.create(input)).rejects.toMatchObject({code: 'NETWORK_ERROR'});
    return pending();
}

describe('historical ingestion original operations', () => {
    it('persists exact identity and input before POST, retains unknown and blocks a new identity', async () => {
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async (url, body) => {
            const original = JSON.parse(stored.get(key)!);
            expect(original.request).toEqual({kind: 'CREATE', jobId: id, input});
            expect(url).toBe(`/marketdata/ingestion-jobs/${id}`);
            expect(body).toEqual(input);
            throw {status: 0, code: 'NETWORK_ERROR'};
        });
        await expect(ingestionOperations.create(input)).rejects.toMatchObject({
            code: 'NETWORK_ERROR',
        });
        expect(pending().phase).toBe('UNKNOWN');
        await expect(ingestionOperations.create(input)).rejects.toMatchObject({
            code: 'INGESTION_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
        expect(readIngestion(18)).toEqual({kind: 'none'});
    });

    it('requires an explicit missing lookup before resuming the same original request', async () => {
        const original = await lostCreate();
        await expect(ingestionOperations.resume(original)).rejects.toMatchObject({
            code: 'INGESTION_LOOKUP_REQUIRED',
        });
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await expect(ingestionOperations.lookup(original)).rejects.toMatchObject({status: 404});
        vi.mocked(apiClient.post).mockResolvedValue({data: job()});
        await expect(ingestionOperations.resume(original)).resolves.toEqual(job());
        expect(apiClient.post).toHaveBeenLastCalledWith(`/marketdata/ingestion-jobs/${id}`, input);
        expect(pending().phase).toBe('CONFIRMED');
        expect(pending().request).toEqual(original.request);
    });

    it('confirms creation by exact result GET without repeating the command', async () => {
        const original = await lostCreate();
        const get = vi
            .spyOn(apiClient, 'get')
            .mockResolvedValue({data: {...job(), status: 'FAILED'}});
        await ingestionOperations.lookup(original);
        expect(get).toHaveBeenCalledWith(`/marketdata/ingestion-jobs/${id}/creation-result`);
        expect(apiClient.post).toHaveBeenCalledTimes(1);
        expect(pending().phase).toBe('CONFIRMED');
        expect(pending().result?.status).toBe('FAILED');
        await ingestionOperations.acknowledge(pending());
        expect(readIngestion(17)).toEqual({kind: 'none'});
    });

    it('retains FAILED as a confirmed run result and does not automatically execute again', async () => {
        const result = {
            ...run(),
            status: 'FAILED',
            fetchedBars: 0,
            insertedBars: 0,
            errorMessage: 'PUBLIC_HISTORY_OUTBOUND_DISABLED',
        };
        const post = vi.spyOn(apiClient, 'post').mockResolvedValue({data: result});
        await expect(ingestionOperations.run(otherId)).resolves.toEqual(result);
        expect(post).toHaveBeenCalledWith(`/marketdata/ingestion-jobs/${otherId}/runs/${id}`);
        await expect(ingestionOperations.run(otherId)).rejects.toMatchObject({
            code: 'INGESTION_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
        expect(pending().phase).toBe('CONFIRMED');
    });

    it('looks up a lost run using both original identifiers', async () => {
        vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0});
        await ingestionOperations.run(otherId).catch(() => undefined);
        const original = pending();
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: run()});
        await expect(ingestionOperations.lookup(original)).resolves.toEqual(run());
        expect(get).toHaveBeenCalledWith(`/marketdata/ingestion-jobs/${otherId}/runs/${id}`);
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });

    it('does not confirm an unrelated identity, changed input or another creator', async () => {
        const original = await lostCreate();
        for (const wrong of [
            {...job(), jobId: otherId},
            {...job(), symbol: 'ETH-USDT'},
            {...job(), endTime: '2026-10-01T00:04:00Z'},
            {...job(), createdBy: 'other-user'},
        ]) {
            expect(() => confirmIngestion(original, wrong)).toThrow('INGESTION_RESPONSE_INVALID');
        }
        expect(pending().phase).toBe('UNKNOWN');
    });

    it('rejects unsafe counts, nonterminal results and mismatched run-to-job relationships', async () => {
        vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0});
        await ingestionOperations.run(otherId).catch(() => undefined);
        for (const wrong of [
            {...run(), jobId: id},
            {...run(), runId: otherId},
            {...run(), status: 'RUNNING'},
            {...run(), fetchedBars: -1},
            {...run(), insertedBars: Number.MAX_SAFE_INTEGER + 1},
            {...run(), finishedAt: null},
        ]) {
            expect(() => confirmIngestion(pending(), wrong)).toThrow('INGESTION_RESPONSE_INVALID');
        }
    });

    it('releases an initial explicit validation rejection but preserves conflicts and rejected recovery', async () => {
        const post = vi.spyOn(apiClient, 'post').mockRejectedValue({status: 400});
        await ingestionOperations.create(input).catch(() => undefined);
        expect(readIngestion(17)).toEqual({kind: 'none'});
        post.mockRejectedValue({status: 409});
        await ingestionOperations.create(input).catch(() => undefined);
        const original = pending();
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await ingestionOperations.lookup(original).catch(() => undefined);
        post.mockRejectedValue({status: 403});
        await ingestionOperations.resume(original).catch(() => undefined);
        expect(pending().phase).toBe('UNKNOWN');
        expect(pending().request).toEqual(original.request);
    });

    it('does not send if storage, readback, coordination or identity generation is unavailable', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('localStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('denied');
            },
        });
        await expect(ingestionOperations.create(input)).rejects.toMatchObject({
            code: 'INGESTION_STORAGE_UNAVAILABLE',
        });
        vi.stubGlobal('localStorage', {getItem: () => null, setItem: () => undefined});
        await expect(ingestionOperations.create(input)).rejects.toMatchObject({
            code: 'INGESTION_STORAGE_UNAVAILABLE',
        });
        vi.stubGlobal('navigator', {});
        await expect(ingestionOperations.create(input)).rejects.toMatchObject({
            code: 'INGESTION_COORDINATION_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });

    it('does not overwrite corrupt persisted operations', async () => {
        stored.set(key, '{broken');
        expect(readIngestion(17)).toEqual({kind: 'unavailable'});
        const post = vi.spyOn(apiClient, 'post');
        await expect(ingestionOperations.create(input)).rejects.toMatchObject({
            code: 'INGESTION_PENDING',
        });
        expect(stored.get(key)).toBe('{broken');
        expect(post).not.toHaveBeenCalled();
    });

    it('uses cross-window exclusion while a command is in flight', async () => {
        let release!: (value: {data: MarketdataIngestionJob}) => void;
        const post = vi.spyOn(apiClient, 'post').mockImplementation(
            () =>
                new Promise((resolve) => {
                    release = resolve;
                }),
        );
        const first = ingestionOperations.create(input);
        await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
        await expect(ingestionOperations.create(input)).rejects.toMatchObject({
            code: 'INGESTION_PENDING',
        });
        release({data: job()});
        await first;
        expect(post).toHaveBeenCalledTimes(1);
    });

    it('keeps late responses out of a different signed-in user', async () => {
        let release!: (value: {data: MarketdataIngestionJob}) => void;
        const post = vi.spyOn(apiClient, 'post').mockImplementation(
            () =>
                new Promise((resolve) => {
                    release = resolve;
                }),
        );
        const submitted = ingestionOperations.create(input);
        await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
        useAuthStore.setState({currentUser: {...user, userId: 18, username: 'other-user'}});
        release({data: job()});
        await expect(submitted).rejects.toMatchObject({code: 'INGESTION_SCOPE_CHANGED'});
        expect(readIngestion(18)).toEqual({kind: 'none'});
        expect(pending().phase).toBe('UNKNOWN');
    });

    it('lets a downgraded owner read and acknowledge but never submit another command', async () => {
        const original = await lostCreate();
        useAuthStore.setState({currentUser: {...user, roles: ['VIEWER']}});
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: job()});
        await ingestionOperations.lookup(original);
        await ingestionOperations.acknowledge(pending());
        await expect(ingestionOperations.run(otherId)).rejects.toMatchObject({
            code: 'INGESTION_READ_ONLY',
        });
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });

    it('normalizes explicit timezone offsets across the DST repeated hour and rejects empty ranges', () => {
        const normalized = normalizeIngestionInput({
            ...input,
            startTime: '2026-11-01T01:30:00-04:00',
            endTime: '2026-11-01T01:30:00-05:00',
        });
        expect(normalized.startTime).toBe('2026-11-01T05:30:00.000Z');
        expect(normalized.endTime).toBe('2026-11-01T06:30:00.000Z');
        for (const endTime of [input.startTime, '2026-09-30T23:59:59Z', '2026-10-01 00:03:00'])
            expect(() => normalizeIngestionInput({...input, endTime})).toThrow(
                'INGESTION_INPUT_INVALID',
            );
    });

    it('offers BNB history only where its formal adapter implements it', () => {
        expect(ingestionSymbols('BINANCE')).toContain('BNB-USDT');
        expect(ingestionSymbols('OKX')).not.toContain('BNB-USDT');
        expect(ingestionSymbols('OTHER')).toEqual([]);
        expect(() => normalizeIngestionInput({...input, symbol: 'BNB-USDT'})).toThrow(
            'INGESTION_INPUT_INVALID',
        );
    });

    it('bounds read windows, validates duplicate identities and forwards cancellation', async () => {
        const signal = new AbortController().signal;
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: [job()]});
        await ingestionReads.jobs(signal);
        expect(get).toHaveBeenLastCalledWith('/marketdata/ingestion-jobs', {signal});
        get.mockResolvedValue({data: [job(), job()]});
        await expect(ingestionReads.jobs()).rejects.toMatchObject({
            code: 'INGESTION_RESPONSE_INVALID',
        });
        get.mockResolvedValue({data: Array.from({length: 101}, job)});
        await expect(ingestionReads.jobs()).rejects.toMatchObject({
            code: 'INGESTION_RESPONSE_INVALID',
        });
        get.mockRejectedValue({status: 503});
        await expect(ingestionReads.jobs()).rejects.toMatchObject({status: 503});
    });

    it('keeps both locales complete for the ingestion workspace', () => {
        const keys = (value: object, prefix = ''): string[] =>
            Object.entries(value).flatMap(([key, child]) =>
                typeof child === 'object' ? keys(child, prefix + key + '.') : [prefix + key],
            );
        expect(keys(zh.ingestion)).toEqual(keys(en.ingestion));
    });
});
