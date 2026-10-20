import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import type {CurrentUser} from '@/types/auth';
import {
    confirmContinuousOperation,
    continuousBudget,
    continuousOperations,
    readContinuousOperation,
    validContinuousOperationRequest,
    type PendingContinuousOperation,
} from '@/features/paper-trading/continuous-operations';

const stored = new Map<string, string>();
const locks = new Set<string>();
const storageKey = 'nq:continuous-operation:v1:17';
const user: CurrentUser = {
    userId: 17,
    username: 'continuous-owner',
    roles: ['OPERATOR'],
    authenticated: true,
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const input = {type: 'CREATE' as const, publishId: 'pub-original', budget: '100.00000000'};
const progress = {
    paperRunId: 'ptr-original',
    status: 'RUNNING',
    startedAt: '2026-10-15T00:00:00Z',
    lastPollAt: null,
    lastSuccessfulPollAt: null,
    lastProcessedBar: '2026-10-14T23:00:00Z',
    nextExpectedBar: '2026-10-15T00:00:00Z',
    lastObservedBar: '2026-10-14T23:00:00Z',
    gapStartBar: null,
    lastDecisionId: null,
    lastDecisionStatus: null,
    blockReason: null,
    consecutivePollFailures: 0,
};

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
        currentUser: user,
        accessToken: 'original-session',
        tokenType: 'Bearer',
    });
});
afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null, accessToken: null});
});

function pending(): PendingContinuousOperation {
    const value = readContinuousOperation(17);
    expect(value.kind).toBe('pending');
    if (value.kind !== 'pending') throw new Error('pending identity required');
    return value.pending;
}
function response(original: PendingContinuousOperation) {
    const request = original.request;
    return {
        operationId: request.operationId,
        type: request.type,
        publishId: request.type === 'CREATE' ? request.publishId : null,
        budget: request.type === 'CREATE' ? '100' : null,
        paperRunId: request.type === 'CREATE' ? progress.paperRunId : request.paperRunId,
        status: 'COMPLETED',
        result: {
            ...progress,
            paperRunId: request.type === 'CREATE' ? progress.paperRunId : request.paperRunId,
            status: request.type === 'PAUSE' ? 'STOPPED' : 'RUNNING',
        },
        completedAt: '2026-10-15T00:01:00Z',
    };
}
async function unknown() {
    const post = vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
    await expect(continuousOperations.submit(input)).rejects.toMatchObject({code: 'NETWORK_ERROR'});
    return {post, original: pending()};
}

describe('continuous original operation recovery', () => {
    it('a bounded read timeout retains the original identity and cannot authorize another submission', async () => {
        const {original} = await unknown();
        vi.useFakeTimers();
        vi.spyOn(apiClient, 'get').mockImplementation(
            (_url, options) =>
                new Promise((_resolve, reject) => {
                    options?.signal?.addEventListener('abort', () =>
                        reject({status: 0, code: 'NETWORK_ERROR'}),
                    );
                }),
        );
        const read = expect(continuousOperations.lookup(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_READ_TIMEOUT',
        });
        await vi.advanceTimersByTimeAsync(15_000);
        await read;
        expect(pending().request.operationId).toBe(original.request.operationId);
        await expect(continuousOperations.resume(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_LOOKUP_REQUIRED',
        });
    });
    it('saves the original identity before POST and retains a lost response without another creation', async () => {
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async (_url, body) => {
            expect(pending().request).toEqual(body);
            throw {status: 0, code: 'NETWORK_ERROR'};
        });
        await expect(continuousOperations.submit(input)).rejects.toMatchObject({
            code: 'NETWORK_ERROR',
        });
        const original = pending();
        expect(original.phase).toBe('UNKNOWN');
        expect(original.request).toMatchObject(input);
        await expect(continuousOperations.submit(input)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
    });

    it('a restored SUBMITTING record becomes unknown without sending any request', () => {
        const post = vi.spyOn(apiClient, 'post');
        const value = {
            ownerUserId: 17,
            request: {...input, operationId: crypto.randomUUID()},
            phase: 'SUBMITTING',
            recordedAt: Date.now(),
        };
        stored.set(storageKey, JSON.stringify(value));
        expect(pending().phase).toBe('UNKNOWN');
        expect(post).not.toHaveBeenCalled();
    });

    it('GET confirms the original result and clears only that original record', async () => {
        const {post, original} = await unknown();
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: response(original)});
        const result = await continuousOperations.lookup(original);
        expect(result.ownerUserId).toBe(17);
        expect(result.result.status).toBe('RUNNING');
        expect(readContinuousOperation(17).kind).toBe('none');
        expect(get).toHaveBeenCalledWith(
            `/paper-trading/strategy-sim/continuous-operations/${original.request.operationId}`,
            {signal: expect.any(AbortSignal)},
        );
        expect(post).toHaveBeenCalledTimes(1);
    });

    it('404 and 503 preserve unknown; another failed read revokes permission to continue based on an old 404', async () => {
        const {post, original} = await unknown();
        const get = vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await expect(continuousOperations.lookup(original)).rejects.toMatchObject({status: 404});
        get.mockRejectedValue({status: 503, code: 'UNAVAILABLE'});
        await expect(continuousOperations.lookup(original)).rejects.toMatchObject({status: 503});
        await expect(continuousOperations.resume(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_LOOKUP_REQUIRED',
        });
        expect(pending().request).toEqual(original.request);
        expect(post).toHaveBeenCalledTimes(1);
    });

    it('continues only after a current-session GET404 and sends exactly the original body and ID', async () => {
        const {post, original} = await unknown();
        await expect(continuousOperations.resume(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_LOOKUP_REQUIRED',
        });
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await continuousOperations.lookup(original).catch(() => undefined);
        post.mockResolvedValue({data: response(original)});
        await continuousOperations.resume(original);
        expect(post.mock.calls[0][1]).toEqual(post.mock.calls[1][1]);
        expect(readContinuousOperation(17).kind).toBe('none');
    });

    it('busy, ID conflict and later admission rejection retain the original identity', async () => {
        const post = vi.spyOn(apiClient, 'post').mockRejectedValue({
            status: 409,
            code: 'STATE_CONFLICT',
            message: 'CONTINUOUS_SIM_OPERATION_BUSY',
        });
        await continuousOperations.submit(input).catch(() => undefined);
        const original = pending();
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await continuousOperations.lookup(original).catch(() => undefined);
        post.mockRejectedValue({status: 400, code: 'BAD_REQUEST'});
        await continuousOperations.resume(original).catch(() => undefined);
        expect(pending().request.operationId).toBe(original.request.operationId);
        await continuousOperations.lookup(original).catch(() => undefined);
        post.mockRejectedValue({
            status: 409,
            code: 'STATE_CONFLICT',
            message: 'CONTINUOUS_SIM_OPERATION_ID_CONFLICT',
        });
        await continuousOperations.resume(original).catch(() => undefined);
        expect(pending().request.operationId).toBe(original.request.operationId);
    });

    it('initial explicit precondition rejection releases a nonexecuted request while preserving failure', async () => {
        vi.spyOn(apiClient, 'post').mockRejectedValue({
            status: 409,
            code: 'STATE_CONFLICT',
            message: 'CONTINUOUS_SIM_DISABLED',
        });
        await expect(continuousOperations.submit(input)).rejects.toMatchObject({status: 409});
        expect(readContinuousOperation(17).kind).toBe('none');
    });

    it('mismatched ID, input, run, action, status, missing fields or corrupt facts never become success', async () => {
        const {original} = await unknown();
        const valid = response(original);
        for (const result of [
            null,
            {...valid, operationId: crypto.randomUUID()},
            {...valid, publishId: 'pub-other'},
            {...valid, budget: '100.00000001'},
            {...valid, type: 'PAUSE'},
            {...valid, status: 'RUNNING'},
            {...valid, completedAt: 'bad-time'},
            {...valid, result: {...valid.result, paperRunId: 'ptr-other'}},
            {...valid, result: {...valid.result, consecutivePollFailures: -1}},
            {...valid, result: {...valid.result, status: 'STOPPED'}},
            {...valid, result: null},
        ]) {
            expect(() => confirmContinuousOperation(original, result)).toThrowError(
                'CONTINUOUS_OPERATION_RESPONSE_INVALID',
            );
        }
        expect(pending().request).toEqual(original.request);
    });

    it('pause result must identify the original run and stays a historical STOPPED result', async () => {
        vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
        await continuousOperations
            .submit({type: 'PAUSE', paperRunId: 'ptr-selected'})
            .catch(() => undefined);
        const original = pending();
        expect(confirmContinuousOperation(original, response(original)).result.status).toBe(
            'STOPPED',
        );
        expect(() =>
            confirmContinuousOperation(original, {...response(original), paperRunId: 'ptr-other'}),
        ).toThrow();
        expect(() =>
            confirmContinuousOperation(original, {
                ...response(original),
                publishId: 'pub-unrelated',
            }),
        ).toThrow();
    });

    it('another user and a changed request cannot query or resume the original record', async () => {
        const {post, original} = await unknown();
        const get = vi.spyOn(apiClient, 'get');
        await expect(
            continuousOperations.lookup({
                ...original,
                request: {
                    ...input,
                    publishId: 'pub-other',
                    operationId: original.request.operationId,
                },
            }),
        ).rejects.toMatchObject({code: 'CONTINUOUS_OPERATION_STORAGE_UNAVAILABLE'});
        useAuthStore.setState({currentUser: {...user, userId: 18}, accessToken: 'other-session'});
        await expect(continuousOperations.lookup(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_SCOPE_CHANGED',
        });
        await expect(continuousOperations.resume(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_SCOPE_CHANGED',
        });
        expect(readContinuousOperation(18).kind).toBe('none');
        expect(stored.has(storageKey)).toBe(true);
        expect(get).not.toHaveBeenCalled();
        expect(post).toHaveBeenCalledTimes(1);
    });

    it('same-user session change invalidates a previous GET404 permission', async () => {
        const {post, original} = await unknown();
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await continuousOperations.lookup(original).catch(() => undefined);
        useAuthStore.setState({accessToken: 'renewed-session'});
        await expect(continuousOperations.resume(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_LOOKUP_REQUIRED',
        });
        expect(post).toHaveBeenCalledTimes(1);
    });

    it('role downgrade preserves the original operation and permits only its read result', async () => {
        const {post, original} = await unknown();
        useAuthStore.setState({currentUser: {...user, roles: ['VIEWER']}});
        await expect(continuousOperations.resume(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_PERMISSION',
        });
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: response(original)});
        await continuousOperations.lookup(original);
        expect(readContinuousOperation(17).kind).toBe('none');
        expect(post).toHaveBeenCalledTimes(1);
    });

    it('session change during a successful POST does not clear the old pending identity', async () => {
        let complete!: (value: {data: unknown}) => void;
        const post = vi.spyOn(apiClient, 'post').mockImplementation(
            () =>
                new Promise((resolve) => {
                    complete = resolve;
                }),
        );
        const operation = continuousOperations.submit(input);
        await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
        const original = pending();
        useAuthStore.setState({accessToken: 'changed-while-pending'});
        complete({data: response(original)});
        await expect(operation).rejects.toMatchObject({code: 'CONTINUOUS_OPERATION_SCOPE_CHANGED'});
        expect(pending().request).toEqual(original.request);
    });

    it('cross-page coordination admits only one simultaneous submission', async () => {
        let reject!: (value: unknown) => void;
        const post = vi.spyOn(apiClient, 'post').mockImplementation(
            () =>
                new Promise((_resolve, fail) => {
                    reject = fail;
                }),
        );
        const first = continuousOperations.submit(input);
        await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
        await expect(continuousOperations.submit(input)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_PENDING',
        });
        reject({status: 0, code: 'NETWORK_ERROR'});
        await expect(first).rejects.toMatchObject({code: 'NETWORK_ERROR'});
        expect(post).toHaveBeenCalledTimes(1);
    });

    it('corrupt, blocked, silently failing storage or unsupported coordination never sends POST', async () => {
        const post = vi.spyOn(apiClient, 'post');
        stored.set(storageKey, 'corrupt');
        expect(readContinuousOperation(17).kind).toBe('unavailable');
        await expect(continuousOperations.submit(input)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_PENDING',
        });
        stored.clear();
        vi.stubGlobal('localStorage', {getItem: () => null, setItem: () => undefined});
        await expect(continuousOperations.submit(input)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_STORAGE_UNAVAILABLE',
        });
        vi.stubGlobal('localStorage', {
            getItem: () => {
                throw new Error('blocked');
            },
        });
        await expect(continuousOperations.submit(input)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_PENDING',
        });
        vi.stubGlobal('navigator', {});
        await expect(continuousOperations.submit(input)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_COORDINATION_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });

    it('a failed storage removal keeps the confirmed ID blocked rather than enabling a new action', async () => {
        const {original} = await unknown();
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: response(original)});
        vi.stubGlobal('localStorage', {
            getItem: (key: string) => stored.get(key) ?? null,
            removeItem: () => undefined,
        });
        await expect(continuousOperations.lookup(original)).rejects.toMatchObject({
            code: 'CONTINUOUS_OPERATION_STORAGE_UNAVAILABLE',
        });
        expect(pending().request).toEqual(original.request);
    });

    it('budget validation preserves exact minimum and maximum and rejects mixed request shapes', () => {
        expect(continuousBudget('0.00000001')).toBe('0.00000001');
        expect(continuousBudget('100000.00000000')).toBe('100000');
        expect(continuousBudget('000100.00100000')).toBe('100.001');
        for (const invalid of ['0', '-1', '100000.00000001', '0.000000001', '1e-8', 'NaN', '', 1])
            expect(continuousBudget(invalid)).toBeNull();
        const request = {...input, operationId: crypto.randomUUID()};
        expect(validContinuousOperationRequest(request)).toBe(true);
        expect(validContinuousOperationRequest({...request, paperRunId: 'ptr-other'})).toBe(false);
        expect(validContinuousOperationRequest({...request, unexpected: true})).toBe(false);
        expect(validContinuousOperationRequest({...request, publishId: 'bad\uD800'})).toBe(false);
    });
});
