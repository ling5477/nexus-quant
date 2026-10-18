import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import {
    acknowledgeMaintenance,
    lookupMaintenance,
    readMaintenance,
    submitMaintenance,
    validMaintenance,
} from '@/features/trading/maintenance-operations';

const stored = new Map<string, string>();
const locks = new Set<string>();
const input = {kind: 'RECONCILE' as const, venue: 'OKX' as const, limit: 100};
beforeEach(() => {
    stored.clear();
    locks.clear();
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
        currentUser: {
            userId: 17,
            username: 'synthetic-maintenance-owner',
            roles: ['OPERATOR'],
        } as never,
        accessToken: 'synthetic-session',
    });
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null, accessToken: null});
});
async function unknown() {
    const post = vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0});
    await expect(submitMaintenance(17, input)).rejects.toEqual({status: 0});
    return {post, original: readMaintenance(17)!};
}
function receipt() {
    return {
        operationId: readMaintenance(17)!.operationId,
        ...input,
        state: 'COMPLETED',
        traceId: 'synthetic-maintenance-trace',
        detail: 'new_trades=0',
        createdAt: '2026-10-18T00:00:00Z',
        completedAt: '2026-10-18T00:00:01Z',
    };
}
it('preserves the UUID before the first POST and cannot create another identity after timeout', async () => {
    const {post, original} = await unknown();
    expect(post).toHaveBeenCalledWith(
        '/trading/reconciliation/run-once',
        {venue: 'OKX', limit: 100},
        {headers: {'Idempotency-Key': original.operationId}},
    );
    await expect(submitMaintenance(17, input)).rejects.toMatchObject({code: 'MAINTENANCE_PENDING'});
    expect(post).toHaveBeenCalledTimes(1);
    expect(readMaintenance(17)?.operationId).toBe(original.operationId);
});
it('404 and 503 lookup do not clear UNKNOWN, release it, or resubmit', async () => {
    const {post, original} = await unknown();
    const get = vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
    await expect(lookupMaintenance(original)).rejects.toEqual({status: 404});
    get.mockRejectedValue({status: 503});
    await expect(acknowledgeMaintenance(original)).rejects.toEqual({status: 503});
    expect(readMaintenance(17)).toEqual(original);
    expect(post).toHaveBeenCalledTimes(1);
});
it('rereads the original completed result before acknowledgement and clearing', async () => {
    const {post, original} = await unknown();
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: receipt()});
    expect((await lookupMaintenance(original)).operationId).toBe(original.operationId);
    await acknowledgeMaintenance(original);
    expect(get).toHaveBeenCalledTimes(2);
    expect(readMaintenance(17)).toBeNull();
    expect(post).toHaveBeenCalledTimes(1);
});
it('rejects a completed response belonging to another request or with changed inputs', async () => {
    const {original} = await unknown();
    const get = vi
        .spyOn(apiClient, 'get')
        .mockResolvedValue({data: {...receipt(), operationId: crypto.randomUUID()}});
    await expect(lookupMaintenance(original)).rejects.toMatchObject({
        code: 'MAINTENANCE_RESPONSE_INVALID',
    });
    get.mockResolvedValue({data: {...receipt(), venue: 'BINANCE'}});
    await expect(acknowledgeMaintenance(original)).rejects.toMatchObject({
        code: 'MAINTENANCE_RESPONSE_INVALID',
    });
    expect(readMaintenance(17)).toEqual(original);
});
it('does not release STARTED or UNKNOWN receipts', async () => {
    const {original} = await unknown();
    const get = vi.spyOn(apiClient, 'get');
    for (const state of ['STARTED', 'UNKNOWN']) {
        get.mockResolvedValue({data: {...receipt(), state, detail: null, completedAt: null}});
        await expect(acknowledgeMaintenance(original)).rejects.toMatchObject({
            code: 'MAINTENANCE_PENDING',
        });
        expect(readMaintenance(17)).toEqual(original);
    }
});
it('blocks cross-tab reentry while the initial HTTP request is outstanding', async () => {
    let release!: (value: unknown) => void;
    const post = vi.spyOn(apiClient, 'post').mockImplementation(
        () =>
            new Promise((resolve) => {
                release = resolve;
            }),
    );
    const first = submitMaintenance(17, input);
    await expect(submitMaintenance(17, input)).rejects.toMatchObject({code: 'MAINTENANCE_BUSY'});
    release({data: receipt()});
    await first;
    expect(post).toHaveBeenCalledTimes(1);
});
it('cannot dispatch if storage or coordination is unavailable, and detects damaged pending records', async () => {
    const post = vi.spyOn(apiClient, 'post');
    vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
        throw new Error('fixture quota');
    });
    await expect(submitMaintenance(17, input)).rejects.toMatchObject({
        code: 'MAINTENANCE_STORAGE_UNAVAILABLE',
    });
    expect(post).not.toHaveBeenCalled();
    stored.set('nq:trading-maintenance:v1:17', 'corrupt');
    expect(() => readMaintenance(17)).toThrow('MAINTENANCE_STORAGE_INVALID');
    vi.stubGlobal('navigator', {});
    await expect(submitMaintenance(17, input)).rejects.toMatchObject({
        code: 'MAINTENANCE_COORDINATION_UNAVAILABLE',
    });
});
it('masks another user and rejects stale-session responses', async () => {
    const {original} = await unknown();
    expect(readMaintenance(18)).toBeNull();
    vi.spyOn(apiClient, 'get').mockImplementation(async () => {
        useAuthStore.setState({accessToken: 'different-synthetic-session'});
        return {data: receipt()};
    });
    await expect(lookupMaintenance(original)).rejects.toMatchObject({
        code: 'MAINTENANCE_SCOPE_CHANGED',
    });
    expect(readMaintenance(17)).toEqual(original);
});
it('enforces bounded integer scans and distinguishes recovery without a scan limit', () => {
    for (const limit of [0, -1, 1.5, 1001, NaN])
        expect(validMaintenance({...input, limit})).toBe(false);
    expect(validMaintenance({...input, limit: 1000})).toBe(true);
    expect(validMaintenance({kind: 'RECOVERY', venue: 'BINANCE'})).toBe(true);
    expect(validMaintenance({kind: 'RECOVERY', venue: 'BINANCE', limit: 100})).toBe(false);
});
