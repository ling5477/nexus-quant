import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import type {CurrentUser} from '../../src/types/auth';
import type {DatasetOperation, DatasetOperationRequest} from '../../src/types/marketdata';
import {
    datasetInstant,
    datasetOperationReads,
    datasetOperations,
    normalizeDatasetInput,
    readDatasetOperation,
    validateDatasetOperation,
} from '../../src/features/marketdata/dataset-operations';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

const id = '22222222-2222-4222-8222-222222222222',
    other = '33333333-3333-4333-8333-333333333333';
const key = 'nq:dataset-operation:v1:17';
const input = {
    datasetName: 'Original BNB data',
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BNB-USDT',
    interval: '1m',
    startTime: '2026-09-20T00:00:00.123456Z',
    endTime: '2026-09-20T01:00:00.123456Z',
};
const user: CurrentUser = {
    userId: 17,
    username: 'dataset-owner',
    authenticated: true,
    roles: ['OPERATOR'],
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const stored = new Map<string, string>(),
    locks = new Set<string>();
let sessionNumber = 0;
function receipt(
    kind: 'CREATE' | 'REFRESH' = 'CREATE',
    state: DatasetOperation['state'] = 'SUCCEEDED',
): DatasetOperation {
    const target = kind === 'CREATE' ? id : other;
    return {
        operationId: id,
        kind,
        datasetId: target,
        state,
        input: kind === 'CREATE' ? {...input} : {},
        createdAt: '2026-10-16T01:00:00Z',
        completedAt: state === 'PENDING' ? null : '2026-10-16T01:00:01Z',
        failureCode:
            state === 'FAILED'
                ? kind === 'CREATE'
                    ? 'DATASET_ID_OCCUPIED'
                    : 'DATASET_NOT_FOUND'
                : null,
        result:
            state !== 'SUCCEEDED'
                ? null
                : {
                      dataset: {
                          ...input,
                          datasetId: target,
                          status: 'INVALID',
                          qualityStatus: 'INCOMPLETE',
                          barCount: 0,
                          gapCount: 60,
                          source: 'marketdata_bars',
                          createdBy: user.username,
                          createdAt: '2026-10-01T00:00:00Z',
                          updatedAt: '2026-10-16T01:00:00.000001Z',
                          requestJson: JSON.stringify(
                              kind === 'CREATE' ? input : {legacyDataset: true},
                          ),
                      },
                      coverage: {
                          coverageId: id,
                          datasetId: target,
                          rangeStartTime: input.startTime,
                          rangeEndTime: input.endTime,
                          expectedBars: 60,
                          actualBars: 0,
                          missingBars: 60,
                          duplicateBars: 0,
                          invalidBars: 0,
                          qualityStatus: 'INCOMPLETE',
                          summaryJson: '{}',
                          createdAt: '2026-10-16T01:00:00.000001Z',
                      },
                  },
    };
}
function pending() {
    const v = readDatasetOperation(17);
    if (v.kind !== 'saved') throw new Error('Original request must be retained');
    return v.pending;
}
async function lost(kind: 'CREATE' | 'REFRESH' = 'CREATE') {
    vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
    await expect(
        kind === 'CREATE' ? datasetOperations.create(input) : datasetOperations.refresh(other),
    ).rejects.toMatchObject({code: 'NETWORK_ERROR'});
}
beforeEach(() => {
    stored.clear();
    locks.clear();
    vi.stubGlobal('window', {dispatchEvent: vi.fn()});
    vi.stubGlobal('crypto', {randomUUID: () => id});
    vi.stubGlobal('localStorage', {
        getItem: (k: string) => stored.get(k) ?? null,
        setItem: (k: string, value: string) => stored.set(k, value),
        removeItem: (k: string) => stored.delete(k),
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
        accessToken: 'synthetic-dataset-' + ++sessionNumber,
    });
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null, accessToken: null});
});

describe('dataset original request recovery', () => {
    it.each(['CREATE', 'REFRESH'] as const)(
        'saves the complete %s original before POST and blocks a second write',
        async (kind) => {
            const post = vi.spyOn(apiClient, 'post').mockImplementation(async (url, body) => {
                const original = pending();
                expect(original.ownerUserId).toBe(17);
                expect(original.request.kind).toBe(kind);
                expect(original.request.operationId).toBe(id);
                expect(original.request.datasetId).toBe(kind === 'CREATE' ? id : other);
                expect(body).toEqual(original.request.input);
                expect(url).toBe(
                    kind === 'CREATE'
                        ? `/marketdata/datasets/${id}/creation`
                        : `/marketdata/datasets/${other}/quality-refreshes/${id}`,
                );
                throw {status: 0, code: 'NETWORK_ERROR'};
            });
            await expect(
                kind === 'CREATE'
                    ? datasetOperations.create(input)
                    : datasetOperations.refresh(other),
            ).rejects.toMatchObject({code: 'NETWORK_ERROR'});
            expect(pending().phase).toBe('UNKNOWN');
            await expect(datasetOperations.create(input)).rejects.toMatchObject({
                code: 'DATASET_OPERATION_PENDING',
            });
            expect(post).toHaveBeenCalledTimes(1);
            expect(readDatasetOperation(18)).toEqual({kind: 'none'});
        },
    );
    it.each(['CREATE', 'REFRESH'] as const)(
        'only reads the saved %s ID after reload, then acknowledges its terminal receipt',
        async (kind) => {
            await lost(kind);
            vi.spyOn(apiClient, 'get').mockResolvedValue({data: receipt(kind)});
            expect(await datasetOperationReads.one(id)).toEqual(receipt(kind));
            expect(apiClient.post).toHaveBeenCalledTimes(1);
            await datasetOperations.acknowledge();
            expect(readDatasetOperation(17)).toEqual({kind: 'none'});
        },
    );
    it.each(['missing', 'pending'])(
        'requires a %s lookup before explicit same-ID continuation',
        async (mode) => {
            await lost('REFRESH');
            await expect(datasetOperations.resume()).rejects.toMatchObject({
                code: 'DATASET_OPERATION_LOOKUP_REQUIRED',
            });
            const get = vi.spyOn(apiClient, 'get');
            if (mode === 'missing') {
                get.mockRejectedValue({status: 404});
                await expect(datasetOperationReads.one(id)).rejects.toMatchObject({status: 404});
            } else {
                get.mockResolvedValue({data: receipt('REFRESH', 'PENDING')});
                await datasetOperationReads.one(id);
            }
            vi.mocked(apiClient.post).mockResolvedValue({data: receipt('REFRESH')});
            await datasetOperations.resume();
            expect(apiClient.post).toHaveBeenLastCalledWith(
                `/marketdata/datasets/${other}/quality-refreshes/${id}`,
                {},
            );
            expect(pending().request.datasetId).toBe(other);
            expect(pending().phase).toBe('CONFIRMED');
        },
    );
    it('retains a deterministic rejection until acknowledged and never treats it as successful data', async () => {
        vi.spyOn(apiClient, 'post').mockResolvedValue({data: receipt('CREATE', 'FAILED')});
        expect((await datasetOperations.create(input)).state).toBe('FAILED');
        expect(pending().phase).toBe('CONFIRMED');
        await expect(datasetOperations.refresh(other)).rejects.toMatchObject({
            code: 'DATASET_OPERATION_PENDING',
        });
        await datasetOperations.acknowledge();
        expect(stored.has(key)).toBe(false);
    });
    it('allows a renamed current VIEWER to read and acknowledge their own receipt, but not write', async () => {
        await lost();
        useAuthStore.setState({currentUser: {...user, username: 'renamed', roles: ['VIEWER']}});
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: receipt()});
        await datasetOperationReads.one(id);
        await expect(datasetOperations.resume()).rejects.toMatchObject({
            code: 'DATASET_OPERATION_READ_ONLY',
        });
        await datasetOperations.acknowledge();
        expect(stored.has(key)).toBe(false);
    });
    it('does not send when persistence, reread, coordination or identity generation fails', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('localStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('full');
            },
        });
        await expect(datasetOperations.create(input)).rejects.toMatchObject({
            code: 'DATASET_OPERATION_STORAGE_UNAVAILABLE',
        });
        vi.stubGlobal('navigator', {});
        await expect(datasetOperations.create(input)).rejects.toMatchObject({
            code: 'DATASET_OPERATION_COORDINATION_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });
    it('does not discard an original if acknowledgement cannot remove it', async () => {
        await lost();
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: receipt()});
        await datasetOperationReads.one(id);
        vi.stubGlobal('localStorage', {
            getItem: (k: string) => stored.get(k) ?? null,
            removeItem: () => {
                throw new Error('blocked');
            },
        });
        await expect(datasetOperations.acknowledge()).rejects.toMatchObject({
            code: 'DATASET_OPERATION_STORAGE_UNAVAILABLE',
        });
        expect(pending().request.operationId).toBe(id);
    });
    it('rejects late responses after the login session changes', async () => {
        let finish!: (value: unknown) => void;
        vi.spyOn(apiClient, 'post').mockImplementation(
            () =>
                new Promise((resolve) => {
                    finish = resolve;
                }),
        );
        const active = datasetOperations.create(input);
        await vi.waitFor(() => expect(apiClient.post).toHaveBeenCalledTimes(1));
        useAuthStore.setState({
            currentUser: {...user, userId: 18},
            accessToken: 'different-session',
        });
        finish({data: receipt()});
        await expect(active).rejects.toMatchObject({code: 'DATASET_OPERATION_SCOPE_CHANGED'});
        expect(readDatasetOperation(17).kind).toBe('saved');
        expect(readDatasetOperation(18)).toEqual({kind: 'none'});
    });
    it('coordinates concurrent create and refresh attempts through the same owner slot', async () => {
        let finish!: (value: unknown) => void;
        vi.spyOn(apiClient, 'post').mockImplementation(
            () =>
                new Promise((resolve) => {
                    finish = resolve;
                }),
        );
        const active = datasetOperations.create(input);
        await vi.waitFor(() => expect(apiClient.post).toHaveBeenCalledTimes(1));
        await expect(datasetOperations.refresh(other)).rejects.toMatchObject({
            code: 'DATASET_OPERATION_PENDING',
        });
        finish({data: receipt()});
        await active;
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('does not replace a saved original when the server returns another operation or microsecond input', async () => {
        await lost();
        const changed = receipt();
        changed.input = {...input, startTime: '2026-09-20T00:00:00.123457Z'};
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: changed});
        await expect(datasetOperationReads.one(id)).rejects.toMatchObject({
            code: 'DATASET_OPERATION_RESPONSE_INVALID',
        });
        expect(pending().request.input).toEqual(input);
        await expect(datasetOperations.acknowledge()).rejects.toMatchObject({
            code: 'DATASET_OPERATION_LOOKUP_REQUIRED',
        });
    });
});

describe('microsecond inputs and original result integrity', () => {
    it('normalizes offset instants without dropping microseconds, including pre-epoch values', () => {
        expect(datasetInstant('1969-12-31T23:59:59.999999Z')).toEqual({
            value: '1969-12-31T23:59:59.999999Z',
            micros: -1n,
        });
        expect(datasetInstant('2026-09-20T08:00:00.123456000+08:00').value).toBe(input.startTime);
        expect(datasetInstant('2026-09-20T00:00:00.100000Z').value).toBe(
            '2026-09-20T00:00:00.100Z',
        );
        expect(
            normalizeDatasetInput({
                ...input,
                datasetName: ' data ',
                symbol: 'bnb-usdt',
                startTime: '2026-09-20T08:00:00.123456+08:00',
            }),
        ).toEqual({...input, datasetName: 'data'});
    });
    it.each([
        '2026-02-30T00:00:00Z',
        '2026-01-01T24:00:00Z',
        '2026-01-01T00:00:00.000000001Z',
        '2026-01-01T00:00:00+18:01',
    ])('rejects non-round-trippable or invalid time %s', (time) => {
        expect(() => datasetInstant(time)).toThrow();
    });
    it('rejects equal or reversed times at microsecond precision and unsupported inputs', () => {
        expect(() => normalizeDatasetInput({...input, endTime: input.startTime})).toThrow();
        expect(() =>
            normalizeDatasetInput({...input, endTime: '2026-09-20T00:00:00.123455Z'}),
        ).toThrow();
        expect(() => normalizeDatasetInput({...input, symbol: 'FAKE-USDT'})).toThrow();
        expect(() => normalizeDatasetInput({...input, datasetName: 'bad\nname'})).toThrow();
    });
    it('accepts an operation completed with incomplete data and a refresh of historical request metadata', () => {
        expect(validateDatasetOperation(receipt(), id).result?.dataset.status).toBe('INVALID');
        expect(validateDatasetOperation(receipt('REFRESH'), id).result?.coverage.actualBars).toBe(
            0,
        );
    });
    it.each([
        (r: DatasetOperation) => {
            r.operationId = other;
        },
        (r: DatasetOperation) => {
            r.result!.coverage.coverageId = other;
        },
        (r: DatasetOperation) => {
            r.result!.dataset.barCount = 1;
        },
        (r: DatasetOperation) => {
            r.result!.coverage.createdAt = '2026-10-16T01:00:00.000002Z';
        },
        (r: DatasetOperation) => {
            r.result!.coverage.actualBars = Number.MAX_SAFE_INTEGER + 1;
        },
        (r: DatasetOperation) => {
            r.result!.coverage.rangeStartTime = '2026-09-20T00:00:00.123457Z';
        },
        (r: DatasetOperation) => {
            r.result!.dataset.requestJson = '{}';
        },
        (r: DatasetOperation) => {
            r.result!.dataset.source = 'OKX_PUBLIC_CAPTURE';
        },
        (r: DatasetOperation) => {
            r.result!.dataset.qualityStatus = 'OK';
        },
    ])('rejects a forged or incomplete original receipt %#', (change) => {
        const r = receipt();
        change(r);
        expect(() => validateDatasetOperation(r, id)).toThrow();
    });
    it('rejects a mismatched original request even when the receipt is internally consistent', () => {
        const original: DatasetOperationRequest = {
            kind: 'CREATE',
            operationId: id,
            datasetId: id,
            input: {...input, datasetName: 'Different'},
        };
        expect(() => validateDatasetOperation(receipt(), id, original)).toThrow();
    });
    it('keeps corrupt local records unavailable and provides both locale dictionaries', () => {
        stored.set(key, '{broken');
        expect(readDatasetOperation(17)).toEqual({kind: 'unavailable'});
        expect(zh.datasetRecovery.kinds.CREATE).toBeTruthy();
        expect(en.datasetRecovery.kinds.REFRESH).toBeTruthy();
        expect(Object.keys(zh.datasetRecovery.errors).sort()).toEqual(
            Object.keys(en.datasetRecovery.errors).sort(),
        );
    });
});
