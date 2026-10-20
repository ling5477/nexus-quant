import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import {
    credentialOperationRejected,
    credentialOperationsApi,
    hasCredentialOperationRecord,
    readPendingCredentialOperation,
} from '../../src/features/accounts/api/credential-operations';
import type {CurrentUser} from '../../src/types/auth';

const identity = 'bbbb0000-1111-2222-3333-444444444444';
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

describe('credential result recovery and material lifetime', () => {
    it('retains only non-sensitive identity after lost response, reloads as unknown and resumes the same identity', async () => {
        const post = vi
            .spyOn(apiClient, 'post')
            .mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
        const request = {
            type: 'CREATE' as const,
            credentialType: 'OKX_API_V5' as const,
            apiKey: 'synthetic-api-key',
            secretKey: 'synthetic-secret',
            passphrase: 'synthetic-pass',
        };
        await expect(credentialOperationsApi.submit(5, request)).rejects.toMatchObject({
            code: 'NETWORK_ERROR',
        });
        const pending = readPendingCredentialOperation(1)!;
        expect(pending.phase).toBe('UNKNOWN');
        expect(pending.accountId).toBe(5);
        expect(post).toHaveBeenCalledTimes(1);
        const persisted = [...stored.values()].join('');
        for (const value of [
            request.apiKey,
            request.secretKey,
            request.passphrase,
            'apiKey',
            'secretKey',
            'passphrase',
            'reason',
        ])
            expect(persisted).not.toContain(value);
        await expect(credentialOperationsApi.submit(5, request)).rejects.toMatchObject({
            code: 'CREDENTIAL_OPERATION_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
        post.mockResolvedValue({
            data: {
                operationId: pending.operationId,
                type: 'CREATE',
                status: 'SUCCEEDED',
                completedAt: '2026-10-13T00:00:00Z',
                result: {exchangeAccountId: 5, credentialId: 7, credentialType: 'OKX_API_V5'},
            },
        });
        await credentialOperationsApi.recover(pending, request);
        expect(post.mock.calls[1][0]).toBe(post.mock.calls[0][0]);
        expect(post.mock.calls[1][1]).toEqual(request);
        expect(readPendingCredentialOperation(1)).toBeNull();
    });
    it('never treats missing receipt as a failed write or automatically retries', async () => {
        stored.set(
            'nq:credential-operation:1',
            JSON.stringify({
                ownerUserId: 1,
                accountId: 5,
                operationId: identity,
                type: 'VERIFY',
                credentialId: 7,
                credentialType: 'OKX_API_V5',
                phase: 'SUBMITTING',
            }),
        );
        const pending = readPendingCredentialOperation(1)!;
        expect(pending.phase).toBe('UNKNOWN');
        expect(readPendingCredentialOperation(2)).toBeNull();
        const post = vi.spyOn(apiClient, 'post');
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await expect(credentialOperationsApi.result(pending)).rejects.toMatchObject({status: 404});
        expect(hasCredentialOperationRecord(1)).toBe(true);
        expect(post).not.toHaveBeenCalled();
    });
    it('retains identity on replay conflicts, key changes and authentication changes', () => {
        for (const status of [0, 408, 500, 503, 504])
            expect(credentialOperationRejected({status, code: 'FAILED'})).toBe(false);
        expect(
            credentialOperationRejected({
                status: 409,
                code: 'CREDENTIAL_OPERATION_IDENTITY_CONFLICT',
            }),
        ).toBe(false);
        for (const status of [400, 401, 403, 404, 409, 422]) {
            expect(credentialOperationRejected({status, code: 'REJECTED'})).toBe(true);
            expect(credentialOperationRejected({status, code: 'REJECTED'}, true)).toBe(false);
        }
    });
    it('does not permit corrupted or material-bearing storage to trigger new writes', async () => {
        stored.set(
            'nq:credential-operation:1',
            JSON.stringify({
                ownerUserId: 1,
                accountId: 5,
                operationId: identity,
                type: 'CREATE',
                credentialType: 'OKX_API_V5',
                phase: 'UNKNOWN',
                secretKey: 'synthetic-rejected',
            }),
        );
        expect(readPendingCredentialOperation(1)).toBeNull();
        expect(hasCredentialOperationRecord(1)).toBe(true);
        const post = vi.spyOn(apiClient, 'post');
        await expect(
            credentialOperationsApi.submit(5, {
                type: 'VERIFY',
                credentialType: 'OKX_API_V5',
                credentialId: 7,
            }),
        ).rejects.toMatchObject({code: 'CREDENTIAL_OPERATION_PENDING'});
        expect(post).not.toHaveBeenCalled();
    });
    it('blocks when identity cannot be retained and validates the result object rather than HTTP success alone', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('sessionStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('synthetic denied');
            },
            removeItem: () => {},
        });
        await expect(
            credentialOperationsApi.submit(5, {
                type: 'VERIFY',
                credentialType: 'OKX_API_V5',
                credentialId: 7,
            }),
        ).rejects.toMatchObject({code: 'CREDENTIAL_OPERATION_STORAGE_UNAVAILABLE'});
        expect(post).not.toHaveBeenCalled();
    });
    it('keeps wrong-account or wrong-version responses unknown and prevents recovery across users', async () => {
        vi.spyOn(apiClient, 'post').mockImplementation(async (url) => ({
            data: {
                operationId: String(url).split('/').at(-1),
                type: 'VERIFY',
                status: 'SUCCEEDED',
                result: {exchangeAccountId: 5, credentialId: 8, credentialType: 'OKX_API_V5'},
            },
        }));
        await expect(
            credentialOperationsApi.submit(5, {
                type: 'VERIFY',
                credentialType: 'OKX_API_V5',
                credentialId: 7,
            }),
        ).rejects.toMatchObject({code: 'CREDENTIAL_OPERATION_RESPONSE_INVALID'});
        const pending = readPendingCredentialOperation(1)!;
        expect(pending.phase).toBe('UNKNOWN');
        useAuthStore.setState({currentUser: {...user, userId: 2}});
        await expect(
            credentialOperationsApi.recover(pending, {
                type: 'VERIFY',
                credentialType: 'OKX_API_V5',
                credentialId: 7,
            }),
        ).rejects.toMatchObject({code: 'CREDENTIAL_OPERATION_SCOPE_CHANGED'});
    });
});
