import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import {
    accountFeesApi,
    feeSyncRejected,
    hasFeeSyncRecord,
    readPendingFeeSync,
    validateFeeReceipt,
    type AccountFeeObservation,
    type PendingFeeSync,
} from '../../src/features/accounts/api/fees';
import type {CurrentUser} from '../../src/types/auth';

/** 证明浏览器身份、未知结果与精确值合同；HTTP替身不等于真实费率来源或正式业务验收。 */
const id = 'bbbb0000-1111-2222-3333-444444444444';
const stored = new Map<string, string>();
const user: CurrentUser = {
    userId: 1,
    username: 'fixture',
    roles: ['OPERATOR'],
    authenticated: true,
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const pending: PendingFeeSync = {
    ownerUserId: 1,
    accountId: 5,
    credentialId: 7,
    symbol: 'BTC-USDT',
    operationId: id,
    phase: 'UNKNOWN',
};
const receipt = (operationId = id): AccountFeeObservation => ({
    observationId: 'aaaa0000-1111-2222-3333-444444444444',
    operationId,
    exchangeAccountId: 5,
    credentialId: 7,
    exchangeCode: 'OKX',
    tradeEnv: 'SIM',
    symbol: 'BTC-USDT',
    source: 'OKX_PRIVATE_SPOT_FEE_V5',
    rateConvention: 'OKX_NEGATIVE_COST_V1',
    makerProviderRate: '0.000200000000',
    takerProviderRate: '-0.001000000000',
    makerCostRate: '-0.000200000000',
    takerCostRate: '0.001000000000',
    tier: 'FixtureLv1',
    providerAt: '2026-10-13T00:00:00.123456Z',
    observedAt: '2026-10-13T00:00:00.123456Z',
    expiresAt: '2026-10-13T00:05:00.123456Z',
    sourceSha256: 'a'.repeat(64),
    status: 'SUCCEEDED',
    scope: 'CURRENT_ACCOUNT_AT_OBSERVATION',
});
beforeEach(() => {
    stored.clear();
    vi.stubGlobal('window', {dispatchEvent: vi.fn()});
    vi.stubGlobal('sessionStorage', {
        getItem: (key: string) => stored.get(key) ?? null,
        setItem: (key: string, value: string) => stored.set(key, value),
        removeItem: (key: string) => stored.delete(key),
    });
    useAuthStore.setState({currentUser: user});
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null});
});

describe('fee observation identity and precision', () => {
    it('persists only a target identity after a lost response and explicitly resumes that identity', async () => {
        const post = vi
            .spyOn(apiClient, 'post')
            .mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
        await expect(accountFeesApi.submit(5, 7, 'BTC-USDT')).rejects.toMatchObject({
            code: 'NETWORK_ERROR',
        });
        const original = readPendingFeeSync(1)!;
        expect(original.phase).toBe('UNKNOWN');
        expect(original.accountId).toBe(5);
        expect(original.credentialId).toBe(7);
        const saved = JSON.parse(stored.get('nq:account-fee-sync:1')!);
        expect(Object.keys(saved).sort()).toEqual([
            'accountId',
            'credentialId',
            'operationId',
            'ownerUserId',
            'phase',
            'symbol',
        ]);
        await expect(accountFeesApi.submit(5, 7, 'ETH-USDT')).rejects.toMatchObject({
            code: 'FEE_SYNC_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
        post.mockResolvedValue({data: receipt(original.operationId)});
        await accountFeesApi.recover(original);
        expect(post.mock.calls[1][0]).toBe(post.mock.calls[0][0]);
        expect(post.mock.calls[1][1]).toEqual({credentialId: 7, symbol: 'BTC-USDT'});
        expect(readPendingFeeSync(1)).toBeNull();
    });
    it('reloads an in-flight identity as unknown and does not turn a missing result into a new request', async () => {
        stored.set('nq:account-fee-sync:1', JSON.stringify({...pending, phase: 'SUBMITTING'}));
        const original = readPendingFeeSync(1)!;
        expect(original.phase).toBe('UNKNOWN');
        expect(readPendingFeeSync(2)).toBeNull();
        const post = vi.spyOn(apiClient, 'post');
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await expect(accountFeesApi.result(original)).rejects.toMatchObject({status: 404});
        expect(hasFeeSyncRecord(1)).toBe(true);
        expect(post).not.toHaveBeenCalled();
    });
    it('blocks corrupted or material-bearing records from allowing a new identity', async () => {
        stored.set(
            'nq:account-fee-sync:1',
            JSON.stringify({...pending, apiKey: 'synthetic-rejected-material'}),
        );
        expect(readPendingFeeSync(1)).toBeNull();
        expect(hasFeeSyncRecord(1)).toBe(true);
        const post = vi.spyOn(apiClient, 'post');
        await expect(accountFeesApi.submit(5, 7, 'BTC-USDT')).rejects.toMatchObject({
            code: 'FEE_SYNC_PENDING',
        });
        expect(post).not.toHaveBeenCalled();
    });
    it('never sends when operation identity cannot be saved', async () => {
        vi.stubGlobal('sessionStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('synthetic denied');
            },
        });
        const post = vi.spyOn(apiClient, 'post');
        await expect(accountFeesApi.submit(5, 7, 'BTC-USDT')).rejects.toMatchObject({
            code: 'FEE_SYNC_STORAGE_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });
    it('keeps conflicts, network failures and all recovery failures unknown', () => {
        for (const status of [0, 408, 409, 500, 503, 504])
            expect(feeSyncRejected({status})).toBe(false);
        for (const status of [400, 401, 403, 404, 422]) {
            expect(feeSyncRejected({status})).toBe(true);
            expect(feeSyncRejected({status}, true)).toBe(false);
        }
    });
    it('rejects wrong target, wrong provenance and floating point receipt values', () => {
        expect(() => validateFeeReceipt(pending, receipt())).not.toThrow();
        for (const changed of [
            {exchangeAccountId: 8},
            {credentialId: 9},
            {symbol: 'ETH-USDT'},
            {source: 'ASSUMPTION'},
            {scope: 'HISTORICAL_ACTUAL'},
            {rateConvention: 'ABSOLUTE'},
            {sourceSha256: 'invalid'},
            {observedAt: 'invalid'},
            {makerCostRate: 0.0002},
        ]) {
            expect(() =>
                validateFeeReceipt(pending, {...receipt(), ...changed} as AccountFeeObservation),
            ).toThrow('FEE_SYNC_RESPONSE_INVALID');
        }
    });
    it('retains an invalid success response for original result recovery', async () => {
        vi.spyOn(apiClient, 'post').mockImplementation(async (url) => ({
            data: {...receipt(String(url).split('/').at(-1)), credentialId: 9},
        }));
        await expect(accountFeesApi.submit(5, 7, 'BTC-USDT')).rejects.toMatchObject({
            code: 'FEE_SYNC_RESPONSE_INVALID',
        });
        expect(readPendingFeeSync(1)?.phase).toBe('UNKNOWN');
    });
    it('does not query, resume or clear an old user’s identity after an account switch', async () => {
        stored.set('nq:account-fee-sync:1', JSON.stringify(pending));
        useAuthStore.setState({currentUser: {...user, userId: 2}});
        const post = vi.spyOn(apiClient, 'post'),
            get = vi.spyOn(apiClient, 'get');
        await expect(accountFeesApi.result(pending)).rejects.toMatchObject({
            code: 'FEE_SYNC_SCOPE_CHANGED',
        });
        await expect(accountFeesApi.recover(pending)).rejects.toMatchObject({
            code: 'FEE_SYNC_SCOPE_CHANGED',
        });
        expect(() => accountFeesApi.confirm(pending)).toThrow('FEE_SYNC_SCOPE_CHANGED');
        expect(hasFeeSyncRecord(1)).toBe(true);
        expect(post).not.toHaveBeenCalled();
        expect(get).not.toHaveBeenCalled();
    });
    it('keeps exact signed rate lexemes and provider times in a read without synchronizing', async () => {
        const raw =
            '{"items":[{"makerProviderRate":0.000000000001,"takerProviderRate":-0.001000000000,"makerCostRate":-0.000000000001,"takerCostRate":0.001000000000,"providerAt":"2026-10-13T00:00:00.123456Z"}],"synchronizationAvailable":false}';
        const post = vi.spyOn(apiClient, 'post');
        vi.spyOn(apiClient, 'get').mockImplementation(async (_, config) => {
            const transforms = config?.transformResponse;
            expect(Array.isArray(transforms)).toBe(true);
            return {data: (transforms as Array<(text: string) => unknown>)[0](raw)};
        });
        const history = await accountFeesApi.history(5, 'BTC-USDT');
        expect(history.synchronizationAvailable).toBe(false);
        expect(history.items[0].makerCostRate).toBe('-0.000000000001');
        expect(history.items[0].takerCostRate).toBe('0.001000000000');
        expect(history.items[0].providerAt).toBe('2026-10-13T00:00:00.123456Z');
        expect(post).not.toHaveBeenCalled();
    });
    it('rejects malformed targets before producing a request or pending record', async () => {
        const post = vi.spyOn(apiClient, 'post');
        for (const [account, credential, symbol] of [
            [0, 7, 'BTC-USDT'],
            [5, 0, 'BTC-USDT'],
            [5, 7, 'BTC-USDT-SWAP'],
        ] as const) {
            await expect(accountFeesApi.submit(account, credential, symbol)).rejects.toMatchObject({
                code: 'FEE_SYNC_TARGET_INVALID',
            });
        }
        expect(post).not.toHaveBeenCalled();
        expect(hasFeeSyncRecord(1)).toBe(false);
    });
});
