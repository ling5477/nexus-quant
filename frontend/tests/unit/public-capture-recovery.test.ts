import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import type {CurrentUser} from '../../src/types/auth';
import type {PublicCaptureOperation} from '../../src/types/marketdata';
import {
    captureOperations,
    captureReads,
    normalizeCaptureInput,
    readCapture,
    validateCaptureResult,
    validCaptureId,
} from '../../src/features/marketdata/public-capture-operations';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

const id = '22222222-2222-4222-8222-222222222222';
const otherId = '33333333-3333-4333-8333-333333333333';
const key = 'nq:public-capture:v1:17';
const input = {start: '2026-09-20T00:00:00.000Z', end: '2026-09-21T00:00:00.000Z'};
const user: CurrentUser = {
    userId: 17,
    username: 'capture-owner',
    authenticated: true,
    roles: ['OPERATOR'],
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const stored = new Map<string, string>();
const locks = new Set<string>();
let sessionNumber = 0;
const result = (state: PublicCaptureOperation['state'] = 'SUCCEEDED'): PublicCaptureOperation => ({
    captureId: id,
    state,
    ...input,
    createdAt: '2026-10-16T01:00:00.123456Z',
    completedAt: state === 'PENDING' ? null : '2026-10-16T01:00:01.123456Z',
    failureCode: state === 'FAILED' ? 'PUBLIC_CAPTURE_OUTBOUND_DISABLED' : null,
    result:
        state !== 'SUCCEEDED'
            ? null
            : {
                  datasetId: id,
                  venue: 'OKX',
                  marketType: 'SPOT',
                  instrument: 'BTC-USDT',
                  interval: '1h',
                  ...input,
                  barCount: 24,
                  observedAt: '2026-10-16T01:00:00Z',
                  rawSha256: 'a'.repeat(64),
                  normalizedSha256: 'b'.repeat(64),
                  consumedSha256: 'c'.repeat(64),
                  ruleRawSha256: 'd'.repeat(64),
                  ruleSha256: 'e'.repeat(64),
                  ruleObservedAt: '2026-10-16T01:00:00Z',
                  replayVisibilityVersion: 'CLOSED_HOURLY_BOUNDARY_V1',
                  replayVisibilitySource: 'EXPERIMENT_ASSUMPTION',
                  rulePolicy: 'CURRENTLY_OBSERVED_PUBLIC_RULES',
              },
});
function pending() {
    const value = readCapture(17);
    if (value.kind !== 'saved') throw new Error('Original capture must be retained');
    return value.pending;
}
async function lost() {
    vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
    await expect(captureOperations.create(input)).rejects.toMatchObject({code: 'NETWORK_ERROR'});
}

beforeEach(() => {
    stored.clear();
    locks.clear();
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
        accessToken: 'synthetic-capture-' + ++sessionNumber,
    });
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null, accessToken: null});
});

describe('public capture original request recovery', () => {
    it('persists and rereads the original identity and UTC window before any POST', async () => {
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async (url, body) => {
            const original = JSON.parse(stored.get(key)!);
            expect(original).toMatchObject({
                ownerUserId: 17,
                ownerUsername: user.username,
                captureId: id,
                input,
            });
            expect(url).toBe('/marketdata/public-captures/' + id);
            expect(body).toEqual(input);
            throw {status: 0, code: 'NETWORK_ERROR'};
        });
        await expect(captureOperations.create(input)).rejects.toMatchObject({
            code: 'NETWORK_ERROR',
        });
        expect(pending().phase).toBe('UNKNOWN');
        await expect(captureOperations.create(input)).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
        expect(readCapture(18)).toEqual({kind: 'none'});
    });

    it('refreshes by reading only and never creates a second request after a lost response', async () => {
        await lost();
        expect(readCapture(17).kind).toBe('saved');
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: result()});
        await expect(captureReads.one(id)).resolves.toEqual(result());
        expect(apiClient.post).toHaveBeenCalledTimes(1);
        expect(pending().input).toEqual(input);
        await captureOperations.acknowledge();
        expect(readCapture(17)).toEqual({kind: 'none'});
    });

    it.each(['missing', 'pending'])(
        'continues the exact original ID only after an explicit %s lookup',
        async (mode) => {
            await lost();
            await expect(captureOperations.resume()).rejects.toMatchObject({
                code: 'PUBLIC_CAPTURE_LOOKUP_REQUIRED',
            });
            const get = vi.spyOn(apiClient, 'get');
            if (mode === 'missing') {
                get.mockRejectedValue({status: 404});
                await expect(captureReads.one(id)).rejects.toMatchObject({status: 404});
            } else {
                get.mockResolvedValue({data: result('PENDING')});
                await captureReads.one(id);
            }
            vi.mocked(apiClient.post).mockResolvedValue({data: result()});
            await expect(captureOperations.resume()).resolves.toEqual(result());
            expect(apiClient.post).toHaveBeenLastCalledWith(
                '/marketdata/public-captures/' + id,
                input,
            );
            expect(pending().phase).toBe('CONFIRMED');
            await captureOperations.acknowledge();
            expect(stored.has(key)).toBe(false);
        },
    );

    it.each(['SUCCEEDED', 'FAILED'] as const)(
        'retains the confirmed %s receipt until the user acknowledges it',
        async (state) => {
            vi.spyOn(apiClient, 'post').mockResolvedValue({data: result(state)});
            await expect(captureOperations.create(input)).resolves.toEqual(result(state));
            expect(pending().phase).toBe('CONFIRMED');
            await expect(captureOperations.resume()).rejects.toMatchObject({
                code: 'PUBLIC_CAPTURE_LOOKUP_REQUIRED',
            });
            await expect(captureOperations.create(input)).rejects.toMatchObject({
                code: 'PUBLIC_CAPTURE_PENDING',
            });
            await captureOperations.acknowledge();
            expect(readCapture(17)).toEqual({kind: 'none'});
        },
    );

    it('cannot acknowledge a missing or pending result, or use a failed query as continuation proof', async () => {
        await lost();
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: result('PENDING')});
        await captureReads.one(id);
        await expect(captureOperations.acknowledge()).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_LOOKUP_REQUIRED',
        });
        get.mockRejectedValue({status: 503});
        await expect(captureReads.one(id)).rejects.toMatchObject({status: 503});
        await expect(captureOperations.resume()).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_LOOKUP_REQUIRED',
        });
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });

    it('does not reuse proof after another result is selected', async () => {
        await lost();
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: result()});
        await captureReads.one(id);
        get.mockRejectedValue({status: 404});
        await captureReads.one(otherId).catch(() => undefined);
        await expect(captureOperations.acknowledge()).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_LOOKUP_REQUIRED',
        });
        expect(readCapture(17).kind).toBe('saved');
    });

    it('rejects mismatched result identity, input, provenance and dataset linkage', () => {
        const good = result();
        for (const wrong of [
            {...good, captureId: otherId},
            {...good, end: '2026-09-21T01:00:00Z'},
            {...good, result: {...good.result!, datasetId: otherId}},
            {...good, result: {...good.result!, barCount: 23}},
            {...good, result: {...good.result!, rawSha256: 'missing'}},
            {...good, result: {...good.result!, ruleObservedAt: null}},
            {...good, result: {...good.result!, replayVisibilitySource: 'ACTUAL_EXCHANGE_EVENT'}},
            {...good, result: {...good.result!, rulePolicy: 'HISTORICAL_RULES'}},
            {...good, completedAt: null},
            {...result('PENDING'), result: good.result},
            {...result('FAILED'), failureCode: 'unbounded private source message'},
        ])
            expect(() => validateCaptureResult(wrong, id, input)).toThrow(
                'PUBLIC_CAPTURE_RESPONSE_INVALID',
            );
    });

    it('preserves the original after a malformed write response or HTTP rejection', async () => {
        const post = vi
            .spyOn(apiClient, 'post')
            .mockResolvedValue({data: {...result(), captureId: otherId}});
        await expect(captureOperations.create(input)).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_RESPONSE_INVALID',
        });
        expect(pending().phase).toBe('UNKNOWN');
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await captureReads.one(id).catch(() => undefined);
        post.mockRejectedValue({status: 403});
        await expect(captureOperations.resume()).rejects.toMatchObject({status: 403});
        expect(pending().captureId).toBe(id);
    });

    it('blocks POST if storage cannot be written or read back', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.spyOn(localStorage, 'setItem').mockImplementation(() => undefined);
        await expect(captureOperations.create(input)).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_STORAGE_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
        expect(readCapture(17)).toEqual({kind: 'none'});
    });

    it.each(['invalid json', 'x'.repeat(4097), JSON.stringify({captureId: id})])(
        'does not erase damaged local originals',
        async (raw) => {
            stored.set(key, raw);
            const post = vi.spyOn(apiClient, 'post');
            expect(readCapture(17)).toEqual({kind: 'unavailable'});
            await expect(captureOperations.create(input)).rejects.toMatchObject({
                code: 'PUBLIC_CAPTURE_STORAGE_UNAVAILABLE',
            });
            expect(stored.get(key)).toBe(raw);
            expect(post).not.toHaveBeenCalled();
        },
    );

    it('retains the ID when updating the local phase fails after a committed response', async () => {
        vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
                throw new Error('quota');
            });
            return {data: result()};
        });
        await expect(captureOperations.create(input)).resolves.toEqual(result());
        expect(pending().captureId).toBe(id);
        await captureOperations.acknowledge();
        expect(stored.has(key)).toBe(false);
    });

    it('does not clear the original if acknowledgement storage removal fails', async () => {
        vi.spyOn(apiClient, 'post').mockResolvedValue({data: result()});
        await captureOperations.create(input);
        vi.spyOn(localStorage, 'removeItem').mockImplementation(() => undefined);
        await expect(captureOperations.acknowledge()).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_STORAGE_UNAVAILABLE',
        });
        expect(pending().captureId).toBe(id);
    });

    it('requires browser coordination before allocating an identity or sending a command', async () => {
        vi.stubGlobal('navigator', {});
        const post = vi.spyOn(apiClient, 'post');
        await expect(captureOperations.create(input)).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_COORDINATION_UNAVAILABLE',
        });
        expect(stored.has(key)).toBe(false);
        expect(post).not.toHaveBeenCalled();
    });

    it('prevents a second same-user page from submitting while the first POST is pending', async () => {
        let resolve!: (value: {data: PublicCaptureOperation}) => void;
        const post = vi.spyOn(apiClient, 'post').mockImplementation(
            () =>
                new Promise((done) => {
                    resolve = done;
                }),
        );
        const first = captureOperations.create(input);
        expect(pending().phase).toBe('SUBMITTING');
        await expect(captureOperations.create(input)).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_PENDING',
        });
        resolve({data: result()});
        await first;
        expect(post).toHaveBeenCalledTimes(1);
    });

    it.each(['user', 'token'])(
        'rejects a delayed write result after a %s change',
        async (changed) => {
            vi.spyOn(apiClient, 'post').mockImplementation(async () => {
                if (changed === 'user') useAuthStore.setState({currentUser: {...user, userId: 18}});
                else useAuthStore.setState({accessToken: 'different-session'});
                return {data: result()};
            });
            await expect(captureOperations.create(input)).rejects.toMatchObject({
                code: 'PUBLIC_CAPTURE_SCOPE_CHANGED',
            });
            expect(JSON.parse(stored.get(key)!).captureId).toBe(id);
            expect(stored.has('nq:public-capture:v1:18')).toBe(false);
        },
    );

    it('invalidates acknowledgement proof when the token changes', async () => {
        vi.spyOn(apiClient, 'post').mockResolvedValue({data: result()});
        await captureOperations.create(input);
        useAuthStore.setState({accessToken: 'different-session'});
        await expect(captureOperations.acknowledge()).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_LOOKUP_REQUIRED',
        });
        expect(readCapture(17).kind).toBe('saved');
    });

    it('allows a viewer to retrieve and acknowledge their result but forbids writes', async () => {
        await lost();
        useAuthStore.setState({currentUser: {...user, roles: ['VIEWER']}});
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: result()});
        await expect(captureReads.one(id)).resolves.toEqual(result());
        await expect(captureOperations.resume()).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_READ_ONLY',
        });
        await captureOperations.acknowledge();
        await expect(captureOperations.create(input)).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_READ_ONLY',
        });
    });

    it('rejects zero or malformed identity and never sends it', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('crypto', {randomUUID: () => '00000000-0000-0000-0000-000000000000'});
        await expect(captureOperations.create(input)).rejects.toMatchObject({
            code: 'PUBLIC_CAPTURE_IDENTITY_UNAVAILABLE',
        });
        expect(validCaptureId('not-an-id')).toBe(false);
        expect(post).not.toHaveBeenCalled();
    });
});

describe('public capture calendar and complete-hour boundaries', () => {
    it('converts local offsets to the exact UTC hour and accepts 1 and 100 hours', () => {
        expect(
            normalizeCaptureInput({
                start: '2026-09-20T08:00:00+08:00',
                end: '2026-09-21T08:00:00+08:00',
            }),
        ).toEqual(input);
        for (const hours of [1, 100]) {
            const end = new Date(Date.parse(input.start) + hours * 3600000).toISOString();
            expect(normalizeCaptureInput({start: input.start, end}).end).toBe(end);
        }
    });

    it.each([
        {...input, start: input.end},
        {...input, start: '2026-09-22T00:00:00Z'},
        {...input, start: '2026-09-20T00:00:01Z'},
        {...input, start: '2026-09-20T00:00:00.000000001Z'},
        {...input, start: '2026-02-30T00:00:00Z'},
        {...input, end: '2026-09-24T05:00:00Z'},
        {...input, start: '2026-09-20 00:00:00'},
    ])('rejects reversed, invalid calendar, sub-hour or overlong windows', (value) => {
        expect(() => normalizeCaptureInput(value)).toThrow('PUBLIC_CAPTURE_INPUT_INVALID');
    });

    it('rejects newly submitted open windows but can read an original independently of the local clock', () => {
        const start = Math.floor(Date.now() / 3600000) * 3600000;
        const value = {
            start: new Date(start).toISOString(),
            end: new Date(start + 3600000).toISOString(),
        };
        expect(() => normalizeCaptureInput(value)).toThrow('PUBLIC_CAPTURE_INPUT_INVALID');
        expect(normalizeCaptureInput(value, false)).toEqual(value);
    });

    it('provides matching, meaningful recovery and failure labels in both locales', () => {
        expect(Object.keys(zh.publicCaptureRecovery).sort()).toEqual(
            Object.keys(en.publicCaptureRecovery).sort(),
        );
        expect(Object.keys(zh.publicCaptureRecovery.errors).sort()).toEqual(
            Object.keys(en.publicCaptureRecovery.errors).sort(),
        );
        for (const language of [zh, en]) {
            expect(language.publicCaptureRecovery.unknown.length).toBeGreaterThan(10);
            expect(language.publicCaptureRecovery.continueHelp.length).toBeGreaterThan(10);
            expect(language.publicCaptureRecovery.states.PENDING).not.toBe(
                language.publicCaptureRecovery.states.SUCCEEDED,
            );
        }
    });
});
