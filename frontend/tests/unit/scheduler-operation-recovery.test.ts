import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import {
    hasScheduledOperation,
    readScheduledOperation,
    scheduledOperations,
    validScheduledOperationResult,
} from '../../src/features/scheduler/operations';
import type {CurrentUser} from '../../src/types/auth';

const stored = new Map<string, string>();
const user: CurrentUser = {
    userId: 17,
    username: 'scheduler-fixture',
    roles: ['ADMIN'],
    authenticated: true,
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const request = {type: 'RUN_ONCE' as const, jobKey: 'PAPER_MATCHING'};
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

describe('scheduler original operation recovery', () => {
    it('saves the original ID before POST and preserves it after a lost response without automatically running again', async () => {
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            expect(hasScheduledOperation(17)).toBe(true);
            throw {status: 0, code: 'NETWORK_ERROR'};
        });
        await expect(scheduledOperations.submit(request)).rejects.toMatchObject({
            code: 'NETWORK_ERROR',
        });
        const pending = readScheduledOperation(17)!;
        expect(pending.phase).toBe('UNCONFIRMED');
        expect(pending.request).toMatchObject(request);
        await expect(scheduledOperations.submit(request)).rejects.toMatchObject({
            code: 'SCHEDULER_OPERATION_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
    });
    it('queries the original receipt by GET and clears only a matching completed result', async () => {
        vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
        await scheduledOperations.submit(request).catch(() => undefined);
        const pending = readScheduledOperation(17)!;
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {
                ...pending.request,
                status: 'COMPLETED',
                completedAt: '2026-10-14T00:00:00Z',
                result: 'SUCCESS',
            },
        });
        await scheduledOperations.lookup(pending);
        expect(get).toHaveBeenCalledWith(`/scheduler/operations/${pending.request.operationId}`);
        expect(hasScheduledOperation(17)).toBe(false);
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('retains RUNNING and unknown results and does not replace a missing receipt with failure', async () => {
        vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
        await scheduledOperations.submit(request).catch(() => undefined);
        const pending = readScheduledOperation(17)!;
        const get = vi
            .spyOn(apiClient, 'get')
            .mockResolvedValue({data: {...pending.request, status: 'RUNNING'}});
        await scheduledOperations.lookup(pending);
        expect(hasScheduledOperation(17)).toBe(true);
        get.mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await expect(scheduledOperations.lookup(pending)).rejects.toMatchObject({status: 404});
        expect(readScheduledOperation(17)?.request.operationId).toBe(pending.request.operationId);
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('continues only the original frozen input under the original ID', async () => {
        const post = vi
            .spyOn(apiClient, 'post')
            .mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
        await scheduledOperations.submit(request).catch(() => undefined);
        const pending = readScheduledOperation(17)!;
        post.mockResolvedValue({
            data: {
                ...pending.request,
                status: 'COMPLETED',
                completedAt: '2026-10-14T00:00:00Z',
                result: 'SUCCESS',
            },
        });
        await scheduledOperations.resume(pending);
        expect(post.mock.calls[0][1]).toEqual(post.mock.calls[1][1]);
        expect(hasScheduledOperation(17)).toBe(false);
    });
    it('retains identity conflicts and mismatched server responses rather than declaring success', async () => {
        const post = vi
            .spyOn(apiClient, 'post')
            .mockRejectedValue({status: 409, code: 'SCHEDULER_OPERATION_ID_CONFLICT'});
        await scheduledOperations.submit(request).catch(() => undefined);
        const pending = readScheduledOperation(17)!;
        expect(pending.phase).toBe('UNCONFIRMED');
        post.mockResolvedValue({
            data: {
                ...pending.request,
                jobKey: 'LEDGER_RECONCILIATION',
                status: 'COMPLETED',
                result: 'SUCCESS',
                completedAt: '2026-10-14T00:00:00Z',
            },
        });
        await expect(scheduledOperations.resume(pending)).rejects.toMatchObject({
            code: 'SCHEDULER_OPERATION_RESPONSE_INVALID',
        });
        expect(hasScheduledOperation(17)).toBe(true);
    });
    it('cannot restore another owner or changed input', async () => {
        vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
        await scheduledOperations.submit(request).catch(() => undefined);
        const pending = readScheduledOperation(17)!;
        await expect(
            scheduledOperations.resume({
                ...pending,
                request: {...pending.request, jobKey: 'LEDGER_RECONCILIATION'},
            }),
        ).rejects.toMatchObject({code: 'SCHEDULER_OPERATION_SCOPE_CHANGED'});
        useAuthStore.setState({currentUser: {...user, userId: 18}});
        expect(readScheduledOperation(18)).toBeNull();
        await expect(scheduledOperations.resume(pending)).rejects.toMatchObject({
            code: 'SCHEDULER_OPERATION_SCOPE_CHANGED',
        });
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('does not send when storage cannot retain the identity, or when a corrupted record already exists', async () => {
        const post = vi.spyOn(apiClient, 'post');
        stored.set('nq:scheduled-operation:17', 'corrupt');
        await expect(scheduledOperations.submit(request)).rejects.toMatchObject({
            code: 'SCHEDULER_OPERATION_PENDING',
        });
        expect(readScheduledOperation(17)).toBeNull();
        stored.clear();
        vi.stubGlobal('sessionStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('unavailable');
            },
        });
        await expect(scheduledOperations.submit(request)).rejects.toMatchObject({
            code: 'SCHEDULER_OPERATION_STORAGE_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });
    it('validates original control values and the original version instead of accepting a current unrelated row', () => {
        const original = {
            operationId: 'aaaa0000-1111-2222-3333-444444444444',
            type: 'UPDATE_CONTROL' as const,
            jobKey: 'PAPER_MATCHING',
            fixedDelayMs: 3000,
            expectedVersion: 4,
        };
        const receipt = {
            ...original,
            status: 'COMPLETED' as const,
            createdAt: '2026-10-14T00:00:00Z',
            startedAt: null,
            completedAt: '2026-10-14T00:00:01Z',
            result: null,
            reasonCode: null,
            control: {
                jobKey: 'PAPER_MATCHING',
                fixedDelayMs: 3000,
                version: 5,
                enabled: false,
                nextRunAt: null,
                lastStartedAt: null,
                lastFinishedAt: null,
                lastStatus: 'NEVER_RUN' as const,
                lastErrorCode: null,
                consecutiveFailures: 0,
                activeRunId: null,
                updatedBy: 'scheduler-fixture',
                updatedAt: '2026-10-14T00:00:01Z',
            },
        };
        expect(validScheduledOperationResult(original, receipt)).toBe(true);
        expect(
            validScheduledOperationResult(original, {
                ...receipt,
                control: {...receipt.control!, version: 8},
            }),
        ).toBe(false);
        expect(
            validScheduledOperationResult(original, {
                ...receipt,
                control: {...receipt.control!, fixedDelayMs: 4000},
            }),
        ).toBe(false);
    });
});
