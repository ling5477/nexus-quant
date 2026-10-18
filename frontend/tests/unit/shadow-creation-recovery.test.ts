import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import {
    readShadowCreation,
    shadowCreation,
    type PendingShadowCreation,
} from '../../src/features/validation/shadow-creation';
import type {CurrentUser} from '../../src/types/auth';

const stored = new Map<string, string>();
const lockNames = new Set<string>();
const user: CurrentUser = {
    userId: 17,
    username: 'shadow-owner',
    roles: ['OPERATOR'],
    authenticated: true,
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const publish = 'publish-shadow-source';
const storageKey = 'nq:shadow-creation:v1:17';
const created = {
    shadowRunId: '22222222-2222-4222-8222-222222222222',
    publishRecordId: publish,
    artifactDigest: 'a'.repeat(64),
    bindingMode: 'RELEASE_BOUND',
    status: 'CREATED',
    createdAt: '2026-10-15T00:00:00Z',
    idempotentReplay: false,
};
const queried = {
    ...created,
    creationStatus: 'CREATED',
    runStatus: 'CANCELLED',
    updatedAt: '2026-10-15T00:01:00Z',
};

beforeEach(() => {
    stored.clear();
    lockNames.clear();
    vi.stubGlobal('window', {dispatchEvent: vi.fn()});
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => stored.get(key) ?? null,
        setItem: (key: string, value: string) => stored.set(key, value),
        removeItem: (key: string) => stored.delete(key),
    });
    vi.stubGlobal('navigator', {
        locks: {
            request: async (
                name: string,
                _options: unknown,
                callback: (lock: unknown) => Promise<unknown>,
            ) => {
                if (lockNames.has(name)) return callback(null);
                lockNames.add(name);
                try {
                    return await callback({name, mode: 'exclusive'});
                } finally {
                    lockNames.delete(name);
                }
            },
        },
    });
    useAuthStore.setState({currentUser: user});
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null});
});

async function unknown() {
    vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
    await shadowCreation.submit(publish).catch(() => undefined);
    const value = readShadowCreation(17);
    expect(value.kind).toBe('pending');
    return (value as {kind: 'pending'; pending: PendingShadowCreation}).pending;
}

describe('Shadow durable original creation recovery', () => {
    it('saves the exact original ID before POST, retains lost responses, and prevents another command', async () => {
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            expect(readShadowCreation(17).kind).toBe('pending');
            throw {status: 0, code: 'NETWORK_ERROR'};
        });
        await expect(shadowCreation.submit(publish)).rejects.toMatchObject({code: 'NETWORK_ERROR'});
        const value = readShadowCreation(17);
        expect(value).toMatchObject({
            kind: 'pending',
            pending: {ownerUserId: 17, publishRecordId: publish, phase: 'UNKNOWN'},
        });
        expect([...stored.values()].join('')).not.toMatch(
            /password|accessToken|authorization|roles/i,
        );
        await expect(shadowCreation.submit('another-publish')).rejects.toMatchObject({
            code: 'SHADOW_CREATION_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
    });
    it('confirms only the original GET and preserves creation versus current lifecycle status', async () => {
        const pending = await unknown();
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: queried});
        const result = await shadowCreation.lookup(pending);
        expect(get).toHaveBeenCalledWith(
            `/strategy-releases/${publish}/shadow-run-creation-result`,
            {headers: {'Idempotency-Key': pending.commandIdentity}},
        );
        expect(result).toMatchObject({
            creationStatus: 'CREATED',
            runStatus: 'CANCELLED',
            source: 'GET',
            idempotentReplay: null,
        });
        expect(readShadowCreation(17).kind).toBe('none');
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('requires a fresh 404 GET before an explicit retry and reuses exactly the original input', async () => {
        const pending = await unknown();
        await expect(shadowCreation.resume(pending)).rejects.toMatchObject({
            code: 'SHADOW_CREATION_LOOKUP_REQUIRED',
        });
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await expect(shadowCreation.lookup(pending)).rejects.toMatchObject({status: 404});
        const post = vi.mocked(apiClient.post);
        post.mockResolvedValue({data: {...created, idempotentReplay: true}});
        await shadowCreation.resume(pending);
        expect(post.mock.calls[0]).toEqual(post.mock.calls[1]);
        expect(readShadowCreation(17).kind).toBe('none');
    });
    it('a later admission rejection never erases an earlier unknown result or enables automatic retry', async () => {
        const pending = await unknown();
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await shadowCreation.lookup(pending).catch(() => undefined);
        vi.mocked(apiClient.post).mockRejectedValue({status: 422, code: 'ADMISSION_BLOCKED'});
        await expect(shadowCreation.resume(pending)).rejects.toMatchObject({status: 422});
        expect(readShadowCreation(17)).toMatchObject({
            kind: 'pending',
            pending: {commandIdentity: pending.commandIdentity},
        });
        await expect(shadowCreation.resume(pending)).rejects.toMatchObject({
            code: 'SHADOW_CREATION_LOOKUP_REQUIRED',
        });
        expect(apiClient.post).toHaveBeenCalledTimes(2);
    });
    it.each([503, 401, 403])(
        'an unavailable or denied GET %s retains the original ID',
        async (status) => {
            const pending = await unknown();
            vi.spyOn(apiClient, 'get').mockRejectedValue({status, code: 'UNAVAILABLE'});
            await shadowCreation.lookup(pending).catch(() => undefined);
            expect(readShadowCreation(17)).toMatchObject({
                kind: 'pending',
                pending: {commandIdentity: pending.commandIdentity},
            });
            await expect(shadowCreation.resume(pending)).rejects.toMatchObject({
                code: 'SHADOW_CREATION_LOOKUP_REQUIRED',
            });
            expect(apiClient.post).toHaveBeenCalledTimes(1);
        },
    );
    it.each([
        {publishRecordId: 'other-publish'},
        {shadowRunId: 'not-a-uuid'},
        {creationStatus: 'FAILED'},
        {runStatus: 'SUCCEEDED'},
        {updatedAt: 'not-a-date'},
        {artifactDigest: 'invented'},
    ])(
        'rejects a malformed or unrelated receipt instead of clearing recovery: %j',
        async (delta) => {
            const pending = await unknown();
            vi.spyOn(apiClient, 'get').mockResolvedValue({data: {...queried, ...delta}});
            await expect(shadowCreation.lookup(pending)).rejects.toMatchObject({
                code: 'SHADOW_CREATION_RESPONSE_INVALID',
            });
            expect(readShadowCreation(17).kind).toBe('pending');
        },
    );
    it('keeps another login from sending or consuming the original request', async () => {
        const pending = await unknown();
        const get = vi.spyOn(apiClient, 'get');
        useAuthStore.setState({currentUser: {...user, userId: 18}});
        expect(readShadowCreation(18).kind).toBe('none');
        await expect(shadowCreation.lookup(pending)).rejects.toMatchObject({
            code: 'SHADOW_CREATION_SCOPE_CHANGED',
        });
        expect(get).not.toHaveBeenCalled();
        expect(readShadowCreation(17).kind).toBe('pending');
        useAuthStore.setState({currentUser: user});
        get.mockResolvedValue({data: queried});
        await shadowCreation.lookup(pending);
        expect(readShadowCreation(17).kind).toBe('none');
    });
    it('rejects late responses after owner changes without clearing the original owner record', async () => {
        vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            useAuthStore.setState({currentUser: {...user, userId: 18}});
            return {data: created};
        });
        await expect(shadowCreation.submit(publish)).rejects.toMatchObject({
            code: 'SHADOW_CREATION_SCOPE_CHANGED',
        });
        expect(readShadowCreation(17).kind).toBe('pending');
        expect(readShadowCreation(18).kind).toBe('none');
    });
    it('checks ownership again after acquiring a browser lock and sends no request under a new identity', async () => {
        vi.stubGlobal('navigator', {
            locks: {
                request: async (
                    _name: unknown,
                    _opts: unknown,
                    callback: (lock: unknown) => Promise<unknown>,
                ) => {
                    useAuthStore.setState({currentUser: {...user, userId: 18}});
                    return callback({name: 'lock'});
                },
            },
        });
        const post = vi.spyOn(apiClient, 'post');
        await expect(shadowCreation.submit(publish)).rejects.toMatchObject({
            code: 'SHADOW_CREATION_SCOPE_CHANGED',
        });
        expect(post).not.toHaveBeenCalled();
        expect(readShadowCreation(17).kind).toBe('none');
    });
    it('serializes two tabs of the same owner without replacing the original ID', async () => {
        let resolve: (value: unknown) => void = () => {};
        const post = vi.spyOn(apiClient, 'post').mockImplementation(
            () =>
                new Promise((value) => {
                    resolve = value;
                }),
        );
        const first = shadowCreation.submit(publish);
        await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
        const original = stored.get(storageKey);
        await expect(shadowCreation.submit('another-publish')).rejects.toMatchObject({
            code: 'SHADOW_CREATION_PENDING',
        });
        expect(stored.get(storageKey)).toBe(original);
        expect(post).toHaveBeenCalledTimes(1);
        resolve({data: created});
        await first;
    });
    it('does not POST when browser coordination or durable storage is unavailable', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('navigator', {});
        await expect(shadowCreation.submit(publish)).rejects.toMatchObject({
            code: 'SHADOW_CREATION_COORDINATION_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });
    it('does not POST when saving the stable ID fails', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('localStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('unavailable');
            },
        });
        await expect(shadowCreation.submit(publish)).rejects.toMatchObject({
            code: 'SHADOW_CREATION_STORAGE_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });
    it('corrupt storage is an explicit unavailable state and is never overwritten with a new command', async () => {
        stored.set(storageKey, '{"ownerUserId":17,"publishRecordId":42}');
        const post = vi.spyOn(apiClient, 'post');
        expect(readShadowCreation(17).kind).toBe('unavailable');
        await expect(shadowCreation.submit(publish)).rejects.toMatchObject({
            code: 'SHADOW_CREATION_PENDING',
        });
        expect(post).not.toHaveBeenCalled();
        expect(stored.get(storageKey)).toContain('42');
    });
    it('refresh treats an interrupted SUBMITTING as unknown and only queries the saved command', async () => {
        const pending = await unknown();
        stored.set(storageKey, JSON.stringify({...pending, phase: 'SUBMITTING'}));
        expect(readShadowCreation(17)).toMatchObject({
            kind: 'pending',
            pending: {phase: 'UNKNOWN'},
        });
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: queried});
        await shadowCreation.lookup(pending);
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('a confirmed initial admission rejection frees the never-executed identity but an idempotency conflict does not', async () => {
        const post = vi
            .spyOn(apiClient, 'post')
            .mockRejectedValue({status: 409, code: 'ADMISSION_STALE'});
        await shadowCreation.submit(publish).catch(() => undefined);
        expect(readShadowCreation(17).kind).toBe('none');
        post.mockRejectedValue({status: 409, code: 'IDEMPOTENCY_CONFLICT'});
        await shadowCreation.submit(publish).catch(() => undefined);
        expect(readShadowCreation(17).kind).toBe('pending');
    });
});
