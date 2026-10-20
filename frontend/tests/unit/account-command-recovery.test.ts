import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import {
    accountCommandRejected,
    accountCommandsApi,
    hasAccountCommandRecord,
    readPendingAccountCommand,
} from '../../src/features/accounts/api/commands';
import type {CurrentUser} from '../../src/types/auth';

const identity = 'aaaa0000-1111-2222-3333-444444444444';
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

describe('account command result recovery', () => {
    it('retains the original identity and payload after a lost response and never automatically submits again', async () => {
        const post = vi
            .spyOn(apiClient, 'post')
            .mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
        await expect(
            accountCommandsApi.submit({
                type: 'CREATE',
                exchangeCode: 'OKX',
                tradeEnv: 'SIM',
                accountAlias: 'owned',
            }),
        ).rejects.toMatchObject({code: 'NETWORK_ERROR'});
        const pending = readPendingAccountCommand(1)!;
        expect(pending.phase).toBe('UNKNOWN');
        expect(pending.request.accountAlias).toBe('owned');
        expect(post).toHaveBeenCalledTimes(1);
        await expect(
            accountCommandsApi.submit({
                type: 'CREATE',
                exchangeCode: 'OKX',
                tradeEnv: 'SIM',
                accountAlias: 'second',
            }),
        ).rejects.toMatchObject({code: 'ACCOUNT_COMMAND_PENDING'});
        expect(post).toHaveBeenCalledTimes(1);
        post.mockResolvedValue({
            data: {
                commandId: pending.commandId,
                type: 'CREATE',
                status: 'SUCCEEDED',
                completedAt: '2026-10-13T00:00:00Z',
                result: {exchangeAccountId: 5},
            },
        });
        await accountCommandsApi.recover(pending);
        expect(post.mock.calls[1][0]).toBe(post.mock.calls[0][0]);
        expect(post.mock.calls[1][1]).toEqual(post.mock.calls[0][1]);
        expect(readPendingAccountCommand(1)).toBeNull();
    });
    it('keeps pending identities scoped to the owner and treats a reloaded in-flight request as unknown', () => {
        stored.set(
            'nq:account-command:1',
            JSON.stringify({
                ownerUserId: 1,
                commandId: identity,
                phase: 'SUBMITTING',
                request: {type: 'DISABLE', accountId: 5},
            }),
        );
        expect(readPendingAccountCommand(1)?.phase).toBe('UNKNOWN');
        expect(readPendingAccountCommand(2)).toBeNull();
        expect(hasAccountCommandRecord(1)).toBe(true);
    });
    it('separates definitive rejection from unknown transport, identity and restored authentication outcomes', () => {
        for (const status of [400, 404, 409, 422])
            expect(accountCommandRejected({status, code: 'REJECTED'})).toBe(true);
        for (const status of [0, 408, 500, 502, 503, 504])
            expect(accountCommandRejected({status, code: 'FAILED'})).toBe(false);
        expect(
            accountCommandRejected({status: 409, code: 'ACCOUNT_COMMAND_IDENTITY_CONFLICT'}),
        ).toBe(false);
        expect(accountCommandRejected({status: 403, code: 'FORBIDDEN'})).toBe(true);
        expect(accountCommandRejected({status: 403, code: 'FORBIDDEN'}, true)).toBe(false);
    });
    it('rejects corrupt or credential-bearing restoration data without allowing a new command', async () => {
        const post = vi.spyOn(apiClient, 'post');
        stored.set(
            'nq:account-command:1',
            JSON.stringify({
                ownerUserId: 1,
                commandId: identity,
                phase: 'UNKNOWN',
                request: {type: 'CREATE', apiKey: 'synthetic-forbidden-field'},
            }),
        );
        expect(readPendingAccountCommand(1)).toBeNull();
        expect(hasAccountCommandRecord(1)).toBe(true);
        await expect(
            accountCommandsApi.submit({type: 'ENABLE', accountId: 5}),
        ).rejects.toMatchObject({code: 'ACCOUNT_COMMAND_PENDING'});
        expect(post).not.toHaveBeenCalled();
    });
    it('does not submit if the operation identity cannot be retained locally', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('sessionStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('synthetic storage denied');
            },
            removeItem: () => {},
        });
        await expect(
            accountCommandsApi.submit({type: 'ENABLE', accountId: 5}),
        ).rejects.toMatchObject({code: 'ACCOUNT_COMMAND_STORAGE_UNAVAILABLE'});
        expect(post).not.toHaveBeenCalled();
    });
});
