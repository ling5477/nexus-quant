import {expect, it} from 'vitest';
import {loginErrorMessageKey} from '@/pages/login/loginErrorPresentation';

it('distinguishes credential rejection from expired sessions without leaking server text', () => {
    const error = {status: 401, message: 'account exists and password was wrong'};
    expect(loginErrorMessageKey(error, true)).toBe('errors:login.credentials');
    expect(loginErrorMessageKey(error, false)).toBe('errors:login.expired');
    expect(loginErrorMessageKey({...error, message: 'account does not exist'}, true)).toBe('errors:login.credentials');
});

it('keeps network, account access, rate limit and service failures distinct', () => {
    expect(loginErrorMessageKey({status: 0, code: 'NETWORK_ERROR'}, true)).toBe('errors:login.network');
    expect(loginErrorMessageKey({status: 403}, true)).toBe('errors:login.unavailableAccount');
    expect(loginErrorMessageKey({status: 429}, true)).toBe('errors:login.rateLimited');
    expect(loginErrorMessageKey({status: 503}, true)).toBe('errors:login.service');
});
