import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import type {CurrentUser} from '@/types/auth';
import {
    paperAlertOperations,
    paperAlertFingerprint,
    readPaperAlertOperation,
    validPaperAlertCommand,
    parsePaperAlert,
    type PendingPaperAlertOperation,
    type PaperAlertReceipt,
} from '@/features/paper-trading/paper-alert-operations';

const stored = new Map<string, string>();
const key = 'nq:paper-alert-operation:v1:7';
const user: CurrentUser = {
    userId: 7,
    username: 'alert-owner',
    roles: ['OPERATOR'],
    authenticated: true,
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const command = {
    type: 'CREATE' as const,
    alertType: 'SYSTEM_NOTICE',
    severity: 'LOW',
    title: '提示🚀',
};
const gates = new Map<string, Promise<void>>();

/** 替身保留原生Web Locks语义：写请求只尝试一次，读取排队且可取消，不能把锁忙当404。 */
async function requestLock(
    name: string,
    options: {ifAvailable?: boolean; signal?: AbortSignal},
    callback: (lock: unknown) => Promise<unknown>,
) {
    const previous = gates.get(name);
    if (previous && options.ifAvailable) return callback(null);
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
        release = resolve;
    });
    const chain = previous ? previous.then(() => gate) : gate;
    gates.set(name, chain);
    try {
        if (previous)
            await new Promise<void>((resolve, reject) => {
                const abort = () => reject(new DOMException('Cancelled', 'AbortError'));
                options.signal?.addEventListener('abort', abort, {once: true});
                previous
                    .then(resolve)
                    .finally(() => options.signal?.removeEventListener('abort', abort));
                if (options.signal?.aborted) abort();
            });
        if (options.signal?.aborted) throw new DOMException('Cancelled', 'AbortError');
        return await callback({name});
    } finally {
        release();
        void chain.finally(() => {
            if (gates.get(name) === chain) gates.delete(name);
        });
    }
}
beforeEach(() => {
    stored.clear();
    gates.clear();
    vi.stubGlobal('window', {dispatchEvent: vi.fn()});
    vi.stubGlobal('navigator', {locks: {request: requestLock}});
    vi.stubGlobal('localStorage', {
        getItem: (name: string) => stored.get(name) ?? null,
        setItem: (name: string, value: string) => stored.set(name, value),
        removeItem: (name: string) => stored.delete(name),
    });
    useAuthStore.setState({currentUser: user, accessToken: 'original-token', tokenType: 'Bearer'});
});
afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null, accessToken: null});
});
function pending(): PendingPaperAlertOperation {
    const value = readPaperAlertOperation(7);
    if (value.kind !== 'pending') throw new Error('Original pending identity required');
    return value.pending;
}
async function response(original: PendingPaperAlertOperation): Promise<PaperAlertReceipt> {
    return {
        operationId: original.operationId,
        type: original.command.type,
        paperRunId: original.paperRunId,
        status: 'COMPLETED',
        inputSha256: await paperAlertFingerprint(original),
        completedAt: '2026-10-15T00:00:00.123456Z',
        result: {
            alertId: original.command.type === 'CREATE' ? 'alt-original' : original.command.alertId,
            paperRunId: original.paperRunId,
            alertType: 'SYSTEM_NOTICE',
            severity: 'LOW',
            title: '提示🚀',
            status: {CREATE: 'OPEN', ACK: 'ACKED', RESOLVE: 'RESOLVED'}[original.command.type],
            message: null,
            source: null,
            eventSnapshotJson: '{}',
            acknowledgedBy: original.command.type === 'ACK' ? user.username : null,
            acknowledgedAt: original.command.type === 'ACK' ? '2026-10-15T00:00:00Z' : null,
            resolvedAt: original.command.type === 'RESOLVE' ? '2026-10-15T00:00:00Z' : null,
            createdAt: '2026-10-15T00:00:00Z',
            updatedAt: '2026-10-15T00:00:00Z',
        },
    };
}
async function unknown() {
    const post = vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
    await expect(paperAlertOperations.submit('ptr-one', command)).rejects.toMatchObject({
        code: 'NETWORK_ERROR',
    });
    return {post, original: pending()};
}

describe('paper alert original result recovery', () => {
    it('matches independent length-prefixed UTF8 vectors including null, empty and emoji', async () => {
        const base: PendingPaperAlertOperation = {
            owner: 7,
            operationId: crypto.randomUUID(),
            paperRunId: 'ptr-one',
            command,
            phase: 'UNKNOWN',
            recordedAt: 1,
        };
        expect(await paperAlertFingerprint(base)).toBe(
            'd50c02cf24971f57860ee6a2a67b67b9aabe8a2ca9a4a73cb1b57a0408b48fc8',
        );
        expect(
            await paperAlertFingerprint({
                ...base,
                command: {...command, message: '', source: 'MANUAL', eventSnapshotJson: '[1,2]'},
            }),
        ).toBe('ca552046ebf384874751b799ef9853b923a9fc8ffcc4b99d6d22bfc2b3f3ef08');
        expect(
            await paperAlertFingerprint({...base, command: {type: 'ACK', alertId: 'alt-one'}}),
        ).toBe('e372ee6c96c6ebbf2e796bffe7c3eb40f3c1e6faa0f72ba24bce39d24efb232a');
    });
    it('persists complete original identity before POST and blocks a new operation after response loss', async () => {
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async (url, body) => {
            const original = pending();
            expect(original.command).toEqual(body);
            expect(original.phase).toBe('SUBMITTING');
            expect(url).toBe(
                `/paper-trading/runs/ptr-one/alert-operations/${original.operationId}`,
            );
            throw {status: 0, code: 'NETWORK_ERROR'};
        });
        await paperAlertOperations.submit('ptr-one', command).catch(() => undefined);
        expect(pending().phase).toBe('UNKNOWN');
        await expect(paperAlertOperations.submit('ptr-two', command)).rejects.toMatchObject({
            code: 'PAPER_ALERT_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
        expect(stored.get(key)).not.toContain('original-token');
    });
    it('refresh interprets saved SUBMITTING as unknown and performs no mutation', () => {
        stored.set(
            key,
            JSON.stringify({
                owner: 7,
                operationId: crypto.randomUUID(),
                paperRunId: 'ptr-one',
                command,
                recordedAt: Date.now(),
                phase: 'SUBMITTING',
            }),
        );
        const post = vi.spyOn(apiClient, 'post');
        expect(pending().phase).toBe('UNKNOWN');
        expect(post).not.toHaveBeenCalled();
    });
    it('GET confirms the immutable original snapshot and never sends POST', async () => {
        const {post, original} = await unknown();
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: await response(original)});
        const result = await paperAlertOperations.lookup(original);
        expect(result.result.status).toBe('OPEN');
        expect(readPaperAlertOperation(7)).toEqual({kind: 'none'});
        expect(post).toHaveBeenCalledTimes(1);
    });
    it('source read rejects cross-run, cross-alert and corrupt rows while retaining unfamiliar status as a visible fact', async () => {
        const {original} = await unknown();
        const row = (await response(original)).result;
        expect(() => parsePaperAlert(row, 'ptr-other')).toThrow('PAPER_ALERT_RESPONSE_INVALID');
        expect(() => parsePaperAlert(row, 'ptr-one', 'alt-other')).toThrow(
            'PAPER_ALERT_RESPONSE_INVALID',
        );
        expect(() => parsePaperAlert({...row, resolvedAt: 'bad'}, 'ptr-one')).toThrow(
            'PAPER_ALERT_RESPONSE_INVALID',
        );
        expect(parsePaperAlert({...row, status: 'FUTURE_STATE'}, 'ptr-one').status).toBe(
            'FUTURE_STATE',
        );
    });
    it('only a current session GET404 permits explicit continuation with the exact original ID and input', async () => {
        const {post, original} = await unknown();
        await expect(paperAlertOperations.resume(original)).rejects.toMatchObject({
            code: 'PAPER_ALERT_LOOKUP_REQUIRED',
        });
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await paperAlertOperations.lookup(original).catch(() => undefined);
        post.mockResolvedValue({data: await response(original)});
        await paperAlertOperations.resume(original);
        expect(post.mock.calls[1]).toEqual(post.mock.calls[0]);
        expect(readPaperAlertOperation(7).kind).toBe('none');
    });
    it('503 after 404 revokes old permission and leaves the original identity unknown', async () => {
        const {original} = await unknown();
        const get = vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await paperAlertOperations.lookup(original).catch(() => undefined);
        get.mockRejectedValue({status: 503});
        await paperAlertOperations.lookup(original).catch(() => undefined);
        await expect(paperAlertOperations.resume(original)).rejects.toMatchObject({
            code: 'PAPER_ALERT_LOOKUP_REQUIRED',
        });
        expect(pending().operationId).toBe(original.operationId);
    });
    it('new explicit rejection releases only an unexecuted request; conflict and recovery rejection retain UNKNOWN', async () => {
        const post = vi.spyOn(apiClient, 'post').mockRejectedValue({status: 400});
        await paperAlertOperations.submit('ptr-one', command).catch(() => undefined);
        expect(readPaperAlertOperation(7).kind).toBe('none');
        post.mockRejectedValue({status: 409});
        await paperAlertOperations.submit('ptr-one', command).catch(() => undefined);
        const original = pending();
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await paperAlertOperations.lookup(original).catch(() => undefined);
        post.mockRejectedValue({status: 403});
        await paperAlertOperations.resume(original).catch(() => undefined);
        expect(pending().operationId).toBe(original.operationId);
    });
    it('mismatched UUID, parent, action, fingerprint, state or incomplete result never clears the original', async () => {
        const {original} = await unknown();
        const valid = await response(original);
        const get = vi.spyOn(apiClient, 'get');
        const noMessage = {...valid.result};
        delete (noMessage as Partial<typeof noMessage>).message;
        for (const value of [
            {...valid, operationId: crypto.randomUUID()},
            {...valid, paperRunId: 'ptr-other'},
            {...valid, type: 'ACK'},
            {...valid, inputSha256: '0'.repeat(64)},
            {...valid, result: {...valid.result, status: 'RESOLVED'}},
            {...valid, result: noMessage},
            {...valid, result: {...valid.result, message: undefined}},
            {...valid, result: {...valid.result, acknowledgedBy: 'spoofed'}},
            {...valid, result: {...valid.result, title: 'wrong original'}},
            {...valid, result: {...valid.result, eventSnapshotJson: '{'}},
        ]) {
            get.mockResolvedValue({data: value});
            await expect(paperAlertOperations.lookup(original)).rejects.toMatchObject({
                code: 'PAPER_ALERT_RESPONSE_INVALID',
            });
            expect(pending().operationId).toBe(original.operationId);
        }
    });
    it.each(['ACK', 'RESOLVE'] as const)(
        '%s binds its result alert ID and requires the action timestamp',
        async (type) => {
            vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0});
            await paperAlertOperations
                .submit('ptr-one', {type, alertId: 'alt-one'})
                .catch(() => undefined);
            const original = pending();
            const valid = await response(original);
            const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
                data: {...valid, result: {...valid.result, alertId: 'alt-other'}},
            });
            await expect(paperAlertOperations.lookup(original)).rejects.toMatchObject({
                code: 'PAPER_ALERT_RESPONSE_INVALID',
            });
            get.mockResolvedValue({
                data: {
                    ...valid,
                    result: {
                        ...valid.result,
                        [type === 'ACK' ? 'acknowledgedAt' : 'resolvedAt']: null,
                    },
                },
            });
            await expect(paperAlertOperations.lookup(original)).rejects.toMatchObject({
                code: 'PAPER_ALERT_RESPONSE_INVALID',
            });
            get.mockResolvedValue({data: valid});
            await paperAlertOperations.lookup(original);
            expect(readPaperAlertOperation(7).kind).toBe('none');
        },
    );
    it('a token or owner change during POST retains UNKNOWN and cannot confirm or reuse a prior 404', async () => {
        vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            const valid = await response(pending());
            useAuthStore.setState({accessToken: 'new-token'});
            return {data: valid};
        });
        await expect(paperAlertOperations.submit('ptr-one', command)).rejects.toMatchObject({
            code: 'PAPER_ALERT_SESSION_CHANGED',
        });
        const original = pending();
        const get = vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await paperAlertOperations.lookup(original).catch(() => undefined);
        useAuthStore.setState({accessToken: 'another-token'});
        await expect(paperAlertOperations.resume(original)).rejects.toMatchObject({
            code: 'PAPER_ALERT_LOOKUP_REQUIRED',
        });
        useAuthStore.setState({currentUser: {...user, userId: 8}});
        await expect(paperAlertOperations.lookup(original)).rejects.toMatchObject({
            code: 'PAPER_ALERT_SESSION_CHANGED',
        });
        expect(get).toHaveBeenCalledTimes(1);
        expect(readPaperAlertOperation(7).kind).toBe('pending');
    });
    it('VIEWER can query its prior receipt but cannot submit or continue mutation', async () => {
        const {post, original} = await unknown();
        useAuthStore.setState({currentUser: {...user, roles: ['VIEWER']}});
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await paperAlertOperations.lookup(original).catch(() => undefined);
        await expect(paperAlertOperations.resume(original)).rejects.toMatchObject({
            code: 'PAPER_ALERT_PERMISSION_REQUIRED',
        });
        expect(post).toHaveBeenCalledTimes(1);
        stored.clear();
        await expect(paperAlertOperations.submit('ptr-one', command)).rejects.toMatchObject({
            code: 'PAPER_ALERT_PERMISSION_REQUIRED',
        });
        expect(post).toHaveBeenCalledTimes(1);
    });
    it('corrupt, oversized, unreadable or nonpersistent storage blocks POST rather than erasing pending state', async () => {
        const post = vi.spyOn(apiClient, 'post');
        for (const raw of ['{', 'x'.repeat(131073), JSON.stringify({owner: 7})]) {
            stored.set(key, raw);
            expect(readPaperAlertOperation(7).kind).toBe('unavailable');
            await expect(paperAlertOperations.submit('ptr-one', command)).rejects.toMatchObject({
                code: 'PAPER_ALERT_PENDING',
            });
        }
        stored.clear();
        vi.spyOn(localStorage, 'setItem').mockImplementation(() => undefined);
        await expect(paperAlertOperations.submit('ptr-one', command)).rejects.toMatchObject({
            code: 'PAPER_ALERT_STORAGE_UNAVAILABLE',
        });
        vi.spyOn(localStorage, 'getItem').mockImplementation(() => {
            throw new Error('Storage denied');
        });
        expect(readPaperAlertOperation(7).kind).toBe('unavailable');
        expect(post).not.toHaveBeenCalled();
    });
    it('lack of native coordination never falls back to unsafe POST', async () => {
        vi.stubGlobal('navigator', {});
        const post = vi.spyOn(apiClient, 'post');
        await expect(paperAlertOperations.submit('ptr-one', command)).rejects.toMatchObject({
            code: 'PAPER_ALERT_COORDINATION_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });
    it('a queued read waits for the sending tab instead of issuing GET too early', async () => {
        let release!: () => void;
        const barrier = new Promise<void>((resolve) => {
            release = resolve;
        });
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            await barrier;
            throw {status: 0};
        });
        const write = paperAlertOperations.submit('ptr-one', command).catch(() => undefined);
        await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
        const original = pending();
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: await response(original)});
        const read = paperAlertOperations.lookup(original);
        await Promise.resolve();
        expect(get).not.toHaveBeenCalled();
        await expect(paperAlertOperations.submit('ptr-one', command)).rejects.toMatchObject({
            code: 'PAPER_ALERT_BUSY',
        });
        release();
        await write;
        await read;
        expect(get).toHaveBeenCalledTimes(1);
    });
    it('a read deadline while queued or in HTTP preserves UNKNOWN and does not authorize POST', async () => {
        const {original} = await unknown();
        vi.useFakeTimers();
        const get = vi.spyOn(apiClient, 'get').mockImplementation(
            (_url, config) =>
                new Promise((_resolve, reject) => {
                    config?.signal?.addEventListener('abort', () => reject({status: 0}), {
                        once: true,
                    });
                }),
        );
        const read = expect(paperAlertOperations.lookup(original)).rejects.toMatchObject({
            code: 'PAPER_ALERT_READ_TIMEOUT',
        });
        await vi.advanceTimersByTimeAsync(15000);
        await read;
        expect(get).toHaveBeenCalledTimes(1);
        expect(pending().operationId).toBe(original.operationId);
        await expect(paperAlertOperations.resume(original)).rejects.toMatchObject({
            code: 'PAPER_ALERT_LOOKUP_REQUIRED',
        });
    });
    it('the total read deadline includes waiting for a different tab and never issues premature HTTP', async () => {
        const {original} = await unknown();
        let release!: () => void;
        const held = new Promise<void>((resolve) => {
            release = resolve;
        });
        gates.set(key, held);
        vi.useFakeTimers();
        const get = vi.spyOn(apiClient, 'get');
        const read = expect(paperAlertOperations.lookup(original)).rejects.toMatchObject({
            code: 'PAPER_ALERT_READ_TIMEOUT',
        });
        await vi.advanceTimersByTimeAsync(15000);
        await read;
        expect(get).not.toHaveBeenCalled();
        expect(pending().operationId).toBe(original.operationId);
        release();
    });
    it('rejects invalid Unicode, NUL, mixed commands, invalid JSON values and excessive input', () => {
        for (const value of [
            {...command, title: '\ud800'},
            {...command, title: 'bad\0'},
            {...command, eventSnapshotJson: ''},
            {...command, eventSnapshotJson: '{} {}'},
            {...command, title: 'x'.repeat(256)},
            {...command, alertId: 'alt-one'},
            {type: 'ACK', alertId: 'alt-one', title: 'mixed'},
        ])
            expect(validPaperAlertCommand(value)).toBe(false);
        expect(validPaperAlertCommand({...command, eventSnapshotJson: '[1,2]'})).toBe(true);
    });
});
