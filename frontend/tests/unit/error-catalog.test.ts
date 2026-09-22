import {afterEach, describe, expect, it, vi} from 'vitest';
import {AxiosError, AxiosHeaders} from 'axios';
import {QueryClient} from '@tanstack/react-query';
import i18n, {resources, readLocale, setLocale} from '@/i18n';
import {errorCatalog, resolveErrorCatalog} from '@/errors/catalog';
import {normalizeApiError, formatApiError, describeApiError, showApiError} from '@/api/errors';
import {mutationRetry, shouldRetryQuery} from '@/errors/retry-policy';
import {saveAuthError, readAuthError, clearAuthError} from '@/errors/auth-error';

function response(data: unknown, status = 409, headers = {}) {
    return normalizeApiError(new AxiosError('axios diagnostic', undefined, undefined, undefined, {
        data, status, statusText: 'test', headers, config: {headers: new AxiosHeaders()},
    }));
}

afterEach(async () => { vi.unstubAllGlobals(); await setLocale('zh-CN'); });

describe('stable identity and safe localized errors', () => {
    it('resolves canonical identity before code and HTTP fallback in both languages', async () => {
        const error = response({errorId: 'NQ-TRD-1001', errorKey: 'ORDER_VERSION_CONFLICT', code: 'STATE_CONFLICT', message: 'JDBC private stack', traceId: 'trace-contract'});
        expect(error.code).toBe('STATE_CONFLICT');
        expect(error.errorKey).toBe('ORDER_VERSION_CONFLICT');
        expect(error.message).toBe('JDBC private stack');
        expect(resolveErrorCatalog(error)).toBe(errorCatalog.ORDER_VERSION_CONFLICT);
        expect(formatApiError(error)).toContain('订单已被其他操作更新，请刷新最新状态后重试。');
        await setLocale('en-US');
        expect(formatApiError(error)).toContain('The order was updated by another operation. Refresh the latest state before trying again.');
        expect(formatApiError(error)).toContain('traceId: trace-contract');
        expect(formatApiError(error)).toContain('NQ-TRD-1001');
        expect(formatApiError(error)).not.toContain('JDBC');
        expect(resolveErrorCatalog({errorId: error.errorId, code: 'INTERNAL_ERROR', status: 500})).toBe(errorCatalog.ORDER_VERSION_CONFLICT);
        expect(resolveErrorCatalog({code: 'ORDER_VERSION_CONFLICT', status: 409})).toBe(errorCatalog.ORDER_VERSION_CONFLICT);
        expect(resolveErrorCatalog({errorId: error.errorId, code: 'STATE_CONFLICT', status: 409})).toBe(errorCatalog.ORDER_VERSION_CONFLICT);
        expect(resolveErrorCatalog({code: 'STATE_CONFLICT', status: 409}).errorId).toBeNull();
        expect(resolveErrorCatalog({errorKey: 'ORDER_VERSION_CONFLICT', code: 'STATE_CONFLICT', status: 409})).toBe(errorCatalog.ORDER_VERSION_CONFLICT);
        expect(resolveErrorCatalog({errorId: 'NQ-TRD-1001', errorKey: 'INTERNAL_ERROR', code: 'INTERNAL_ERROR'})).toBe(errorCatalog.ORDER_VERSION_CONFLICT);
        expect(resolveErrorCatalog({errorKey: 'FUTURE_KEY', code: 'FORBIDDEN'})).toBe(errorCatalog.FORBIDDEN);
        expect(formatApiError(error)).toContain('ORDER_VERSION_CONFLICT');
        expect(formatApiError(new Error('SQL private stack'))).not.toContain('SQL');
    });

    it.each([
        [401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'], [409, 'STATE_CONFLICT'],
        [422, 'BUSINESS_REJECTED'], [500, 'INTERNAL_ERROR'], [503, 'INTERNAL_ERROR'],
        [400, 'BAD_REQUEST'], [404, 'RESOURCE_NOT_FOUND'], [418, 'UNKNOWN_ERROR'],
    ])('unknown HTTP %i falls back to %s without losing identity', (status, key) => {
        const error = response({code: 'FUTURE_CODE', traceId: 'future-trace', message: 'SQLException private'}, status);
        expect(resolveErrorCatalog(error).errorKey).toBe(key);
        expect(formatApiError(error)).toContain('FUTURE_CODE');
        expect(formatApiError(error)).toContain('future-trace');
        expect(formatApiError(error)).not.toContain('SQLException');
    });

    it('handles malformed and non-JSON proxy responses without raw output', () => {
        for (const payload of [null, '<html>SQL private</html>', {code: {}, message: {}, fieldErrors: 'bad'}, {fieldErrors: [null, 3, {}]}]) {
            const error = response(payload, 500);
            expect(() => formatApiError(error)).not.toThrow();
            expect(formatApiError(error)).not.toMatch(/undefined|SQL private|\[object Object\]/);
        }
        expect(resolveErrorCatalog(response({code: '__proto__'}, 418)).errorKey).toBe('UNKNOWN_ERROR');
    });

    it('localizes network failures and retains server field diagnostics plus traceId together', async () => {
        expect(resolveErrorCatalog(normalizeApiError(new AxiosError('Network Error'))).errorKey).toBe('NETWORK_ERROR');
        const error = response({
            code: 'VALIDATION_ERROR',
            fieldErrors: [{field: 'quantity', reason: 'must be positive; private diagnostic', rejectedValue: '-1'}],
        }, 400, {'x-trace-id': 'header-trace'});
        expect(error.fieldErrors[0]).toMatchObject({reason: 'must be positive; private diagnostic', rejectedValue: '-1'});
        expect(error.traceId).toBe('header-trace');
        expect(formatApiError(error)).toContain('quantity：请检查此字段。');
        expect(formatApiError(error)).toContain('traceId: header-trace');
        expect(formatApiError(error)).not.toContain('private diagnostic');
        await setLocale('en-US');
        expect(formatApiError(error)).toContain('quantity: check this value.');
        const invalidFieldName = response({code: 'VALIDATION_ERROR', fieldErrors: [{field: 'SQL SELECT * secret', reason: 'private'}]}, 400);
        expect(invalidFieldName.fieldErrors[0].field).toBe('SQL SELECT * secret');
        expect(formatApiError(invalidFieldName)).not.toContain('SQL SELECT');
    });

    it('catalog declares every presentation, localized action and mutation retry=false', () => {
        expect(new Set(Object.values(errorCatalog).map((entry) => entry.presentation)).size).toBe(5);
        for (const entry of Object.values(errorCatalog)) {
            expect(entry.retryPolicy.automaticMutation).toBe(false);
            expect(entry.traceIdVisibility).toBe('visible');
            for (const locale of ['zh-CN', 'en-US']) {
                for (const key of [entry.titleKey, entry.messageKey, entry.actionKey]) {
                    expect(i18n.exists(key, {lng: locale})).toBe(true);
                }
            }
        }
    });

    it('does not automatically replay a conflicted mutation or query', async () => {
        const error = response({code: 'ORDER_VERSION_CONFLICT', errorId: 'NQ-TRD-1001'}, 409);
        const client = new QueryClient({defaultOptions: {mutations: {retry: mutationRetry}, queries: {retry: shouldRetryQuery}}});
        const write = vi.fn().mockRejectedValue(error);
        const read = vi.fn().mockRejectedValue(error);
        try {
            const mutation = client.getMutationCache().build(client, {mutationFn: write});
            await expect(mutation.execute(undefined)).rejects.toBe(error);
            await expect(client.fetchQuery({queryKey: ['order'], queryFn: read})).rejects.toBe(error);
            expect(write).toHaveBeenCalledTimes(1);
            expect(read).toHaveBeenCalledTimes(1);
        } finally { client.clear(); }
    });

    it('uses catalog severity at the mutation presentation entry', () => {
        const sink = {error: vi.fn(), warning: vi.fn()};
        showApiError(response({code: 'ORDER_VERSION_CONFLICT'}), sink);
        expect(sink.warning).toHaveBeenCalledOnce();
        expect(sink.error).not.toHaveBeenCalled();
        expect(describeApiError(response({code: 'VALIDATION_ERROR'}, 400)).catalog.presentation).toBe('INLINE_FORM_ERROR');
    });
});

describe('locale resources and preference', () => {
    it('both languages have identical complete key trees without empty values', () => {
        function flatten(value: object, prefix = ''): Record<string, string> {
            return Object.fromEntries(Object.entries(value).flatMap(([key, item]) =>
                typeof item === 'string' ? [[prefix + key, item]] : Object.entries(flatten(item, prefix + key + '.'))));
        }
        const zh = flatten(resources['zh-CN']);
        const en = flatten(resources['en-US']);
        expect(Object.keys(zh).sort()).toEqual(Object.keys(en).sort());
        for (const [key, value] of Object.entries(zh)) {
            expect(value.trim(), key).not.toBe('');
            expect(en[key].trim(), key).not.toBe('');
            expect([...value.matchAll(/{{\s*([^}]+)\s*}}/g)].map((m) => m[1]).sort(), key)
                .toEqual([...en[key].matchAll(/{{\s*([^}]+)\s*}}/g)].map((m) => m[1]).sort());
        }
    });

    it('defaults to Chinese and persists a supported user selection', async () => {
        const data = new Map<string, string>();
        vi.stubGlobal('localStorage', {getItem: (key: string) => data.get(key), setItem: (key: string, value: string) => data.set(key, value)});
        expect(readLocale()).toBe('zh-CN');
        await setLocale('en-US');
        expect(readLocale()).toBe('en-US');
        await setLocale('zh-CN');
        expect(readLocale()).toBe('zh-CN');
        data.set('nq.locale', 'unsupported');
        expect(readLocale()).toBe('zh-CN');
    });

    it('keeps switching possible with unavailable storage', async () => {
        vi.stubGlobal('localStorage', {getItem: () => {throw new Error('blocked');}, setItem: () => {throw new Error('blocked');}});
        expect(readLocale()).toBe('zh-CN');
        await setLocale('en-US');
        expect(i18n.resolvedLanguage).toBe('en-US');
    });

    it('preserves auth trace across redirect without persisting diagnostics', () => {
        const data = new Map<string, string>();
        vi.stubGlobal('sessionStorage', {getItem: (key: string) => data.get(key), setItem: (key: string, value: string) => data.set(key, value), removeItem: (key: string) => data.delete(key)});
        saveAuthError(response({code: 'UNAUTHORIZED', traceId: 'auth-trace', message: 'private'}, 401));
        expect(readAuthError()).toMatchObject({status: 401, traceId: 'auth-trace'});
        expect([...data.values()].join()).not.toContain('private');
        clearAuthError();
        expect(readAuthError()).toBeNull();
    });
});
